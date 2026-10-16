/* Compile-only stub mirroring the ATAK-CIV 5.6 SDK API. Replaced by the host ATAK app at runtime. */
package gov.tak.api.plugin;

public interface IServiceController {
    <T> T getService(Class<T> serviceClass);

    <T> boolean registerComponent(Class<T> componentClass, T component);

    <T> boolean unregisterComponent(Class<T> componentClass, T component);
}
