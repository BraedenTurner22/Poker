import com.collegeshowdown.poker_project.runtime.card.Card;
import com.collegeshowdown.poker_project.runtime.card.Deck;
import com.collegeshowdown.poker_project.runtime.player.ConnectedPlayer;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.collegeshowdown.poker_project.model.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.stream.Collectors;
import java.util.Collections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.io.Serializable;

public class Pot {

    public List<ConnectedPlayer> playersInPot;
    public int amount;

    public Pot() {
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int newAmount) {
        amount = amount + newAmount;
    }

    public List<ConnectedPlayer> getPlayersInPot() {
        return playersInPot;
    }
}
