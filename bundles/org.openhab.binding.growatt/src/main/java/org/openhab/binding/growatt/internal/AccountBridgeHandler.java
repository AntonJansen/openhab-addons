package org.openhab.binding.growatt.internal;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
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

                        // Get the plant list
                        ContentResponse plantResponse = httpClient
                                .GET("https://" + accountBridgeConfig.getServer() + "/PlantListAPI.do");
                        logger.debug("response: " + plantResponse.getStatus());
                        logger.debug("headers: " + plantResponse.getHeaders());
                        logger.debug("content: " + plantResponse.getContentAsString());

                        responseType = new TypeToken<GenericResponse<PlantList>>() {
                        }.getType();

                        GenericResponse<PlantList> presp = gson.fromJson(plantResponse.getContentAsString(),
                                responseType);
                        logger.debug("Received JSON response: " + gson.toJson(presp));

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
                // TODO Auto-generated catch block
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

}
