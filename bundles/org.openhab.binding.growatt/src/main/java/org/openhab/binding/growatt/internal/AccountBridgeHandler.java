package org.openhab.binding.growatt.internal;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountBridgeHandler extends ConfigStatusBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(AccountBridgeHandler.class);
    private AccountBridgeConfig accountBridgeConfig = null;

    public AccountBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        logger.debug("Initializing account bridge handler.");
        accountBridgeConfig = getConfigAs(AccountBridgeConfig.class);
        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            boolean thingReachable = false; // <background task with long running initialization here>
            // Make a login attempt to see if defined account and server are working.
            // when done do:
            CloseableHttpClient httpclient = HttpClients.createDefault();
            try {
                HttpPost httpPost = new HttpPost("https://server.growatt.com/LoginAPI.do");
                List<NameValuePair> nvps = new ArrayList<>();
                nvps.add(new BasicNameValuePair("userName", "AntonJansen"));
                nvps.add(new BasicNameValuePair("password", "1a36591bceec49c832c79e27cd7e8b73"));
                httpPost.setEntity(new UrlEncodedFormEntity(nvps));
                CloseableHttpResponse response = httpclient.execute(httpPost);

                try {
                    logger.debug(response.getStatusLine().toString());
                    HttpEntity entity2 = response.getEntity();
                    // do something useful with the response body
                    // and ensure it is fully consumed
                    EntityUtils.consume(entity2);
                } catch (IOException e) {
                    logger.error("Unexpected io exception while logging in.", e);
                } finally {
                    response.close();
                }

                if (thingReachable) {
                    updateStatus(ThingStatus.ONLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE);
                }

            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {

            } finally {
                try {
                    httpclient.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
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
        } else if (accountBridgeConfig.getServer().isBlank()) {
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
