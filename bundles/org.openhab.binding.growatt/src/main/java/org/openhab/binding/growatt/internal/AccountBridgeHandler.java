package org.openhab.binding.growatt.internal;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.growatt.internal.discovery.GrowattDiscoveryService;
import org.openhab.binding.growatt.internal.responsetypes.GenericResponse;
import org.openhab.binding.growatt.internal.responsetypes.Login;
import org.openhab.binding.growatt.internal.responsetypes.ParserHelper;
import org.openhab.binding.growatt.internal.responsetypes.Plant;
import org.openhab.binding.growatt.internal.responsetypes.PlantList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class AccountBridgeHandler extends ConfigStatusBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(AccountBridgeHandler.class);
    private AccountBridgeConfig accountBridgeConfig = null;
    private HttpClient httpClient = null;
    private GsonBuilder gsonBuilder = null;
    private Gson gson = null;
    private ScheduledFuture<?> pollingJob;
    private @Nullable GrowattDiscoveryService discoveryService;
    private Map<Long, PlantListener> subscribedPlants = new ConcurrentHashMap<Long, PlantListener>();

    private List<Plant> plantList = new ArrayList<Plant>();

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

                            if (!presp.back.success) {
                                logger.warn("Cannot retrieve plant list. Error {}", presp.back.errCode);
                                throw new RuntimeException(
                                        "Call to plant list api returned error " + presp.back.errCode);
                            }

                            // Update the plants in the bridge
                            setPlants(presp.back.data);

                            // Process the total data that is part of the plantlist data
                            processTotalData(presp);

                            // Process the plant data
                            processPlantsData(presp.back.data);

                            String plantDetailURL = "https://" + accountBridgeConfig.getServer() + "/newTwoPlantAPI.do";

                            ContentResponse plantDetailResponse = httpClient.newRequest(plantDetailURL)
                                    .method(HttpMethod.GET).param("op", "getAllDeviceList").param("plantId", "325704")
                                    .send();

                            logger.debug("response: " + plantDetailResponse.getStatus());
                            logger.debug("headers: " + plantDetailResponse.getHeaders());
                            logger.debug("content: " + plantDetailResponse.getContentAsString());

                            JsonParser jp = new JsonParser();
                            JsonElement je = jp.parse(plantDetailResponse.getContentAsString());

                            logger.debug("JSON content:\n" + gson.toJson(je));

                        } catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException e) {
                            logger.error("Cannot get plant list", e);
                        } catch (NoSuchFieldException e) {
                            logger.error("Requested field does not exist in parsing class.", e);
                        } catch (IllegalAccessException e) {
                            logger.error("Requested field is not accessible in parsing class.", e);
                        }

                    }

                    private void processPlantsData(List<Plant> plants) {
                        Set<Long> processedPlantIDs = new TreeSet<Long>();
                        for (Plant plant : plants) {
                            processedPlantIDs.add(new Long(plant.plantId));
                            if (subscribedPlants.containsKey(new Long(plant.plantId))) {
                                logger.debug("Processing plant with id: {}", plant.plantId);
                                PlantListener plantListener = subscribedPlants.get(new Long(plant.plantId));
                                plantListener.onPlantStateChange(plant);
                            }
                        }
                        // Check if some plants are no longer present and should be offline
                        Set<Long> offlinePlants = new TreeSet<Long>(subscribedPlants.keySet());
                        offlinePlants.removeAll(processedPlantIDs);
                        // Set none processed plants offline
                        for (Long id : offlinePlants) {
                            subscribedPlants.get(id).onPlantGone();
                        }
                    }

                    private void processTotalData(GenericResponse<PlantList> presp)
                            throws NoSuchFieldException, IllegalAccessException {
                        String[] fields = { "currentPowerSum", "CO2Sum", "todayEnergySum", "totalEnergySum" };
                        List<QuantityType<?>> dataValues = ParserHelper.getDataValues(presp.back.totalData, fields);

                        updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_CURRENTPOWERSUM, dataValues.get(0));
                        updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_CO2SUM, dataValues.get(1));
                        updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_TODAYENERGYSUM, dataValues.get(2));
                        updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_TOTALENERGYSUM, dataValues.get(3));

                        // Monetaire value
                        Map<DecimalType, StringType> monValues = ParserHelper.getMonetaireValue(presp.back.totalData,
                                "eTotalMoneyText");
                        monValues.forEach((k, v) -> {
                            updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_TOTALMONEY, k);
                            updateState(GrowattBindingConstants.CHANNEL_ACCOUNT_TOTALMONEYCURRENCY, v);
                        });
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
        pollingJob.cancel(true);
        logger.debug("Polling job stopped.");

        if (httpClient != null) {
            try {
                logger.debug("Stopping httpClient....");
                httpClient.stop();
                logger.debug("httpClient stopped.");
            } catch (Exception e) {
                logger.debug("Cannot stop httpClient", e);
            }
        }

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

    public synchronized List<Plant> getPlants() {
        return plantList;
    }

    private synchronized void setPlants(List<Plant> plants) {
        this.plantList = plants;
    }

    public boolean registerDiscoveryListener(GrowattDiscoveryService listener) {
        if (discoveryService == null) {
            discoveryService = listener;
            getPlants().forEach(listener::addPlantDiscovery);
            return true;
        }

        return false;

    }

    public void unregisterDiscoveryListener() {
        discoveryService = null;
    }

    public void registerPlantStatusListener(PlantListener plantListener) {
        logger.debug("Registering plant status listener {}", plantListener.getPlantId());
        subscribedPlants.put(plantListener.getPlantId(), plantListener);

    }

    public void deregisterPlantStatusListener(PlantListener plantListener) {
        logger.debug("De-Registering plant status listener {}", plantListener.getPlantId());
        subscribedPlants.remove(plantListener.getPlantId());
    }

}
