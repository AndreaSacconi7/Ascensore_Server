package polimi.ascensore.model;

import polimi.ascensore.JPA.Player;
import polimi.ascensore.model.exception.CannotAddPlayerNowException;
import polimi.ascensore.model.exception.PlayerNickNameDoesNotExist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * State of one match.
 * <p>
 * A match is a sequence of sets whose hand size goes 1, 2, ..., max, ..., 2, 1. Each set starts with every
 * player betting how many tricks they will take, then one trick (round) is played per card in hand.
 */
public class Game {

    private final List<GamePlayer> players;

    private final int maxHandSize;

    // 0-based index of the current set; the hand size is derived from it
    private int setIndex;

    // Tricks completed in the current set
    private int round;

    private int betsPlaced;

    private final Deck deck;

    private final TableCard tableCard;

    public Game(int maxHandSize, Random random) {
        this.players = new ArrayList<>();
        this.maxHandSize = maxHandSize;
        this.setIndex = 0;
        this.round = 0;
        this.betsPlaced = 0;
        this.deck = new Deck(random);
        this.tableCard = new TableCard();
    }

    public void startGame() {
        deck.shuffleDeck();
        tableCard.setPlayerListOrder(new ArrayList<>(players));
    }

    /**
     * Deals the hand for the current set and turns up the briscola. The peak set has no briscola card:
     * the first card of each trick sets it (see {@link #isPeakSet()}).
     */
    public void distributeCards() {
        for (GamePlayer p : players) {
            for (int j = 0; j < getSet(); j++) {
                p.addCardToHand(deck.getDeckcards().pop());
            }
        }
        if (!deck.getDeckcards().isEmpty() && !isPeakSet()) {
            tableCard.setBriscola(deck.getDeckcards().pop());
        } else {
            tableCard.setBriscola(null);
        }
    }

    public void addPlayer(Player player, String sessionId) throws CannotAddPlayerNowException {
        for (GamePlayer p : players) {
            if (p.getPlayerState() != PlayerState.IDLE) {
                throw new CannotAddPlayerNowException();
            }
        }
        players.add(new GamePlayer(player, sessionId));
    }

    /**
     * The set with the largest hand, where a four-player match deals the whole deck.
     */
    public boolean isPeakSet() {
        return getSet() == maxHandSize;
    }

    public boolean isLastSet() {
        return setIndex == GameRules.totalSets(maxHandSize) - 1;
    }

    public void nextSet() {
        setIndex++;
        round = 0;
        betsPlaced = 0;
    }

    /**
     * Number of sets already completed; used to rotate who bets first.
     */
    public int getSetsPlayed() {
        return setIndex;
    }

    /**
     * Final standing: players still in the match by descending score, then those who left.
     */
    public List<GamePlayer> endGame() {
        List<GamePlayer> results = new ArrayList<>(players);
        results.sort(Comparator
                .comparing((GamePlayer p) -> p.getPlayerState() == PlayerState.EXIT)
                .thenComparing(GamePlayer::getScore, Comparator.reverseOrder()));
        return results;
    }

    public void updateRound() {
        this.round++;
    }

    public void registerBet() {
        this.betsPlaced++;
    }

    public GamePlayer getPlayerByNickName(String nickname) throws PlayerNickNameDoesNotExist {
        for (GamePlayer p : players) {
            if (p.getNickname().equals(nickname)) {
                return p;
            }
        }
        throw new PlayerNickNameDoesNotExist();
    }

    /**
     * Hand size of the current set. The protocol calls this the set number.
     */
    public int getSet() {
        return GameRules.handSize(setIndex, maxHandSize);
    }

    public int getRound() {
        return round;
    }

    public int getBetsPlaced() {
        return betsPlaced;
    }

    public Deck getDeck() {
        return deck;
    }

    public TableCard getTableCard() {
        return tableCard;
    }

    public List<GamePlayer> getPlayers() {
        return players;
    }
}
