public interface Bridge<T> {
    T getRenamed();

    class BridgeImpl implements Bridge<String> {
        @Override
        public String getRenamed() {
            return "Hello!";
        }
    }
}
