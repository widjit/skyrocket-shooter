/* Compile-only stub mirroring the ATAK 4.8 SDK API. Replaced by the host ATAK app at runtime. */
package gov.tak.api.plugin;

public interface IServiceController {
    <T> T getService(Class<T> serviceClass);
}
