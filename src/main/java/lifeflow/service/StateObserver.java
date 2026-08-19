package lifeflow.service;

/** 
 * An observer that receives notifications when the central application state changes.
 */
public interface StateObserver {
    
    /** 
     * Called whenever the LifeFlow state is successfully committed or reloaded. 
     * Observers should refresh their local views. 
     */
    void onStateChanged();
}
