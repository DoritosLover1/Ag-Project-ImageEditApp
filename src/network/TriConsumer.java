package network;

/**
 * TriConsumer functional interface for handling three-parameter callbacks
 */
@FunctionalInterface
public interface TriConsumer<T, U, V> {
    void accept(T t, U u, V v);
}