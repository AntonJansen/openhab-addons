/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.growatt.internal;

import static org.openhab.binding.growatt.internal.GrowattBindingConstants.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.growatt.internal.discovery.GrowattDiscoveryService;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GrowattHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Anton Jansen - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.growatt", service = ThingHandlerFactory.class)
public class GrowattHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<ThingTypeUID>(
            Arrays.asList(PLANT_TYPE, ACCOUNT_TYPE));

    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(GrowattHandlerFactory.class);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (ACCOUNT_TYPE.equals(thingTypeUID)) {
            AccountBridgeHandler handler = new AccountBridgeHandler((Bridge) thing);
            registerDiscoveryService(handler);
            return handler;
        } else if (PLANT_TYPE.equals(thingTypeUID)) {
            return new PlantHandler(thing);
        }

        return null;
    }

    private void registerDiscoveryService(AccountBridgeHandler handler) {
        GrowattDiscoveryService discoveryService = new GrowattDiscoveryService(handler);
        discoveryService.activate();
        logger.debug("Registering discovery service.");
        this.discoveryServiceRegs.put(handler.getThing().getUID(),
                bundleContext.registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<>()));

    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof AccountBridgeHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                GrowattDiscoveryService service = (GrowattDiscoveryService) bundleContext
                        .getService(serviceReg.getReference());
                logger.debug("Unregistering discovery service");
                serviceReg.unregister();
                if (service != null) {
                    service.deactivate();
                }
            }
        }
    }

}
