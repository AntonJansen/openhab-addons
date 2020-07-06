package org.openhab.binding.growatt.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.MetricPrefix;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.growatt.internal.responsetypes.GenericResponse;
import org.openhab.binding.growatt.internal.responsetypes.Login;
import org.openhab.binding.growatt.internal.responsetypes.PlantList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class AccountBridgeHandler extends ConfigStatusBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(AccountBridgeHandler.class);
    private AccountBridgeConfig accountBridgeConfig = null;
    private HttpClient httpClient = null;
    private GsonBuilder gsonBuilder = null;
    private Gson gson = null;
    private ScheduledFuture<?> pollingJob;

    public AccountBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        logger.debug("Initializing account bridge handler.");
        updateStatus(ThingStatus.UNKNOWN);
        accountBridgeConfig = getConfigAs(AccountBridgeConfig.class);
        httpClient = new HttpClient(new SslContextFactory(true));
        gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();

        try {
            httpClient.start();

            scheduler.execute(() -> {
                boolean thingReachable = false; // <background task with long running initialization here>
                // Make a login attempt to see if defined account and server are working.
                try {

                    Fields fields = new Fields();
                    fields.add("userName", accountBridgeConfig.getUsername());
                    String password = DigestUtils.md5Hex(accountBridgeConfig.getPassword());
                    // Do the little password trick, replace all zeroes with a c
                    password = password.replaceAll("0", "c");
                    fields.add("password", password);

                    ContentResponse response = httpClient
                            .POST("https://" + accountBridgeConfig.getServer() + "/LoginAPI.do")
                            .content(new FormContentProvider(fields)).send();
                    logger.debug("response: " + response.getStatus());
                    logger.debug("headers: " + response.getHeaders());
                    logger.debug("content: " + response.getContentAsString());

                    Type responseType = new TypeToken<GenericResponse<Login>>() {
                    }.getType();

                    GenericResponse<Login> resp = gson.fromJson(response.getContentAsString(), responseType);
                    logger.debug("Received JSON response: " + gson.toJson(resp));

                    if ((response.getStatus() == 200) && (resp.back.success)) {
                        Map<String, String> properties = editProperties();
                        properties.put("User ID", "" + resp.back.userId);
                        updateProperties(properties);

                        thingReachable = true;
                    } else {
                        thingReachable = false;
                    }

                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    logger.error("Cannot make login request.", e);
                }
                // when done do:

                if (thingReachable) {
                    updateStatus(ThingStatus.ONLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE);
                }

                // Start polling job to update channel status

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        // execute some binding specific polling code
                        // Get the plant list
                        ContentResponse plantResponse;
                        try {
                            plantResponse = httpClient
                                    .GET("https://" + accountBridgeConfig.getServer() + "/PlantListAPI.do");

                            logger.debug("response: " + plantResponse.getStatus());
                            logger.debug("headers: " + plantResponse.getHeaders());
                            logger.debug("content: " + plantResponse.getContentAsString());

                            Type responseType = new TypeToken<GenericResponse<PlantList>>() {
                            }.getType();

                            GenericResponse<PlantList> presp = gson.fromJson(plantResponse.getContentAsString(),
                                    responseType);
                            logger.debug("Received JSON response: " + gson.toJson(presp));

                            String[] fields = { "currentPowerSum", "CO2Sum", "todayEnergySum", "totalEnergySum" };
                            List<QuantityType<?>> dataValues = getDataValues(presp.back.totalData, fields);

                            updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_CURRENTPOWERSUM, dataValues.get(0));
                            // TODO add CO2SUM
                            updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_TODAYENERGYSUM, dataValues.get(2));
                            updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_TOTALENERGYSUM, dataValues.get(3));

                        } catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException e) {
                            logger.error("Cannot get plant list", e);
                        } catch (NoSuchFieldException e) {
                            logger.error("Requested field does not exist in parsing class.", e);
                        } catch (IllegalAccessException e) {
                            logger.error("Requested field is not accessible in parsing class.", e);
                        }

                    }

                };
                logger.debug("Starting channel poller ....");
                pollingJob = scheduler.scheduleAtFixedRate(runnable, 0, 30, TimeUnit.SECONDS);

            });

        } catch (Exception ex) {
            logger.error("Unexpected error during initialization of account bridge.", ex);
        }

    }

    @Override
    public void dispose() {
        if (httpClient != null) {
            try {
                logger.debug("Stopping httpClient....");
                httpClient.stop();
                logger.debug("httpClient stopped.");
            } catch (Exception e) {
                logger.debug("Cannot stop httpClient", e);
            }
        }
        pollingJob.cancel(true);
    }

    private List<QuantityType<?>> getDataValues(Object dataObject, String[] fieldNames)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        List<QuantityType<?>> result = new ArrayList<QuantityType<?>>();
        @NonNull
        QuantityType<?> dataValue = new QuantityType<>();

        for (String fieldName : fieldNames) {
            // Check if the data Object has the field
            Field field = dataObject.getClass().getDeclaredField(fieldName);
            Type fieldType = field.getGenericType();
            logger.debug("Field {} is of type {}", field, fieldType);
            switch (fieldType.toString()) {
                case "class java.lang.String":
                    String[] dataStrings = ((String) field.get(dataObject)).split("\\s+");
                    if (dataStrings.length != 2) {
                        throw new NoSuchFieldException("Response data does not consist of 2 fields, but of "
                                + dataStrings.length + " fields. For data: " + ((String) field.get(dataObject)));
                    }
                    logger.debug("Value: " + dataStrings[0]);
                    logger.debug("Unit: " + dataStrings[1]);

                    BigDecimal value = new BigDecimal(dataStrings[0]).setScale(1, RoundingMode.HALF_UP);
                    logger.debug("BigDecimal value: " + value);

                    // Now let's find out which unit to use
                    switch (dataStrings[1]) {
                        case "W":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.WATT);
                            break;
                        case "kW":
                            dataValue = new QuantityType<>(value, MetricPrefix.KILO(SmartHomeUnits.WATT));
                            break;
                        case "MW":
                            dataValue = new QuantityType<>(value, MetricPrefix.MEGA(SmartHomeUnits.WATT));
                            break;
                        case "Wh":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.WATT_HOUR);
                            break;
                        case "kWh":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.KILOWATT_HOUR);
                            break;
                        case "MWh":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.MEGAWATT_HOUR);
                            break;
                        case "GWh":
                            dataValue = new QuantityType<>(value, MetricPrefix.GIGA(SmartHomeUnits.WATT_HOUR));
                            break;
                        case "T":
                            dataValue = new QuantityType<>(value, MetricPrefix.MEGA(SIUnits.GRAM));
                            break;
                        default:
                            throw new NoSuchFieldException("Unit type of " + dataStrings[1] + " is not a known type.");
                    } // End of unit switch
                    break; // End of string value
                default:
                    throw new NoSuchFieldException("Cannot process fields of type " + fieldType);
            }

            // add the created QuantityType to the list
            result.add(dataValue);

        }

        return result;
    }

    @Override
    public Collection<@NonNull ConfigStatusMessage> getConfigStatus() {
        logger.debug("getConfigStatus()");
        Collection<ConfigStatusMessage> configStatusMessages;
        configStatusMessages = Collections.emptyList();

        if (accountBridgeConfig.getPassword() == "password") {
            configStatusMessages.add(ConfigStatusMessage.Builder.error("password")
                    .withMessageKeySuffix("Password not defined.").build());
        } else if (accountBridgeConfig.getUsername() == "user") {
            configStatusMessages.add(ConfigStatusMessage.Builder.error("username")
                    .withMessageKeySuffix("Username not defined.").build());
        } else if (accountBridgeConfig.getServer() == "") {
            configStatusMessages.add(ConfigStatusMessage.Builder.error("server name")
                    .withMessageKeySuffix("Server name hosting API not defined.").build());
        } else {
            configStatusMessages = Collections.emptyList();
        }

        return configStatusMessages;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub
        logger.debug("Handle command on channel: " + channelUID + " with command: " + command);

    }

}
