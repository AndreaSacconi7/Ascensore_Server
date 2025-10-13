package polimi.ascensore.network.message;

public class TextMessage implements ExecutableInClient {

    private final String text;

    public TextMessage(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
