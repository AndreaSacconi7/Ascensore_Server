package polimi.ascensore;

import lombok.Getter;
import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Stack;

public class Deck {

    @Getter
    private Stack<Card> deckcards;

    transient Gson gson = new Gson();

    //Array di carte in formato JSON che verrà utilizzato per la creazione del mazzo
    private Card[] cardJson;

    public Deck() {
        this.deckcards = new Stack<>();
        this.cardJson = new Card[40];
    }

    public void createCardDeck() throws FileNotFoundException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        InputStream is = getClass().getResourceAsStream("/CardsFiles/ResourceCardFile.json");
        if (is == null) {
            throw new FileNotFoundException("Il file 'CardFile.json' non è stato trovato");
        }
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        cardJson = gson.fromJson(reader, Card[].class);

        for (Card card : cardJson) {
            deckcards.push(card);
        }

        Collections.shuffle(deckcards);
    }

    //metodo chiamato quando si effettua la lettura con stream
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        gson = new Gson();
    }

}
