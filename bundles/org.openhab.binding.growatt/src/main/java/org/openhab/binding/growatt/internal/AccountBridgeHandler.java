package org.openhab.binding.growatt.internal;

import java.util.Collection;
import java.util.Collections;

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

    @Override
    public void initialize() {
        logger.debug("Initializing account bridge handler.");
        accountBridgeConfig = getConfigAs(AccountBridgeConfig.class);
        updateStatus(ThingStatus.UNKNOWN);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            logger.error("Cannot sleep", e);
        }
        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public Collection<@NonNull ConfigStatusMessage> getConfigStatus() {
        Collection<ConfigStatusMessage> configStatusMessages;
        if (accountBridgeConfig.getPassword() == "password") {
            configStatusMessages = Collections.singletonList(ConfigStatusMessage.Builder.error("password")
                    .withMessageKeySuffix("Password not defined.").build());
        }
        if (accountBridgeConfig.getUsername() == "user") {
            configStatusMessages = Collections.singletonList(ConfigStatusMessage.Builder.error("username")
                    .withMessageKeySuffix("Username not defined.").build());
        } else {
            configStatusMessages = Collections.emptyList();
        }

        return configStatusMessages;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub

    }

}
