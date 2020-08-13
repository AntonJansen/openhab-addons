package org.openhab.binding.growatt.internal;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.growatt.internal.responsetypes.Plant;

public interface PlantListener {

    /**
     * This method returns the plant id of the listener
     *
     * @return Long with the plant id
     */
    Long getPlantId();

    /**
     * This method is called whenever the plant is no longer present
     */
    void onPlantGone();

    /**
     * This method is called when new data is available for the plant
     *
     * @param plant all the new data for the plant
     */
    void onPlantStateChange(@NonNull Plant plant);

}
