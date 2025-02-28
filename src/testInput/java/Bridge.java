public interface Bridge<T> {
    T getT();

    class BridgeImpl implements Bridge<String> {
        @Override
        public String getT() {
            return "Hello!";
        }
    }
}
