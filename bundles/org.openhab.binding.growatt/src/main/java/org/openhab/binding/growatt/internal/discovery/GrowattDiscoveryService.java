package org.openhab.binding.growatt.internal.discovery;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.growatt.internal.AccountBridgeHandler;
import org.openhab.binding.growatt.internal.GrowattBindingConstants;
import org.openhab.binding.growatt.internal.responsetypes.GrowattObject;
import org.openhab.binding.growatt.internal.responsetypes.Plant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrowattDiscoveryService extends AbstractDiscoveryService {
    private AccountBridgeHandler accountBridgeHandler;
    private final Logger logger = LoggerFactory.getLogger(GrowattDiscoveryService.class);

    public GrowattDiscoveryService(AccountBridgeHandler accountBridgeHandler) {
        super(30);
        this.accountBridgeHandler = accountBridgeHandler;
    }

    @Override
    protected void startScan() {
        logger.debug("Starting discovery scan ...");
        List<Plant> plantList = accountBridgeHandler.getPlants();
        for (Plant p : plantList) {
            addPlantDiscovery(p);
        }

    }

    public void activate() {
        logger.debug("Discovery activated.");
        accountBridgeHandler.registerDiscoveryListener(this);
    }

    @Override
    public void deactivate() {
        removeOlderResults(new Date().getTime(), accountBridgeHandler.getThing().getUID());
        accountBridgeHandler.unregisterDiscoveryListener();
    }

    public void addPlantDiscovery(Plant plant) {
        ThingUID thingUID = getThingUID(plant);
        ThingTypeUID thingTypeUID = getThingTypeUID(plant);

        if (thingUID != null && thingTypeUID != null) {
            ThingUID bridgeUID = accountBridgeHandler.getThing().getUID();
            Map<String, Object> properties = new HashMap<>();
            properties.put("plantId", plant.getId());
            properties.put("nameOfPlant", plant.plantName);

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                    .withProperties(properties).withBridge(bridgeUID).withRepresentationProperty("plantId")
                    .withLabel(plant.plantName).build();

            thingDiscovered(discoveryResult);
        } else {
            logger.debug("discovered unsupported plant with id {}", plant.getId());
        }

    }

    private @Nullable ThingUID getThingUID(GrowattObject growattObject) {
        ThingUID bridgeUID = accountBridgeHandler.getThing().getUID();
        ThingTypeUID thingTypeUID = getThingTypeUID(growattObject);

        if (thingTypeUID != null && getSupportedThingTypes().contains(thingTypeUID)) {
            return new ThingUID(thingTypeUID, bridgeUID, growattObject.getId());
        } else {
            return null;
        }
    }

    private @Nullable ThingTypeUID getThingTypeUID(GrowattObject growattObject) {
        if (growattObject instanceof Plant) {
            return GrowattBindingConstants.PLANT_TYPE;
        } else {
            logger.error("Unknown growatt object type: " + growattObject.getClass().getCanonicalName() + " with id: "
                    + growattObject.getId());
        }

        return null;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return GrowattBindingConstants.SUPPORTED_THING_TYPES_FOR_DISCOVERY;
    }

}
