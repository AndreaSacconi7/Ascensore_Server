package polimi.ascensore;

import lombok.Getter;
import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Stack;

public class Deck {

    @Getter
    private Stack<Card> deckcards;

    transient Gson gson = new Gson();

    private Card[] cardJson;

    public Deck() {
        this.deckcards = new Stack<>();
        this.cardJson = new Card[40];
    }

    public Stack<Card> createCardDeck() throws FileNotFoundException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        InputStream is = getClass().getResourceAsStream("/CardsFiles/ResourceCardFile.json");
        if (is == null) {
            throw new FileNotFoundException("Il file 'CardFile.json' non è stato trovato");
        }
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        cardJson = gson.fromJson(reader, Card[].class);

        for (int i = 0; i < cardJson.length; i++){
            deckcards.push(cardJson[i]);
            Collections.shuffle(deckcards);
        }

        return deckcards;
    }

}
