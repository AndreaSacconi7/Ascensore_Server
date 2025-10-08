package polimi.ascensore.model;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Stack;

public class Deck {

    private Stack<Card> deckcards;

    transient Gson gson = new Gson();

    //stack di carte che non viene modificato ma viene usato per ripristinare il mazzo
    private Stack<Card> cardStack;

    public Deck() {
        this.deckcards = new Stack<>();
        this.cardStack = new Stack<>();
    }

    public void createCardDeck() throws FileNotFoundException {
        InputStream is = getClass().getResourceAsStream("/CardsFiles/Card.json");
        if (is == null) {
            throw new FileNotFoundException("Il file 'CardFile.json' non è stato trovato");
        }
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        Card[] cardJson = gson.fromJson(reader, Card[].class);

        for (Card card : cardJson) {
            deckcards.push(card);
            cardStack.push(card);
        }

        Collections.shuffle(deckcards);
    }

    public void shuffleDeck(){
        deckcards.clear();
        deckcards.addAll(cardStack);
        Collections.shuffle(deckcards);
    }

    //metodo chiamato quando si effettua la lettura con stream
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        gson = new Gson();
    }

    public Stack<Card> getDeckcards() {
        return cardStack;
    }
}
