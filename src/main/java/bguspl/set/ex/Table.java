package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
/////////// Added ///////////
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.ArrayDeque;
/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /////////// Added ///////////
    private volatile List<List<Integer>> tokens;//players list which contains slots token placed on
    public final int maxToken =3;
    private List <Integer> availableSlots;
    public Semaphore sem; 
    public Queue <Integer> playersCardCheck;
    public final int notExits = -1;
    public final int first =0;
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.playersCardCheck=new ArrayDeque<Integer>();
        this.sem=new Semaphore(1,true);
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokens=new ArrayList<List<Integer>>();
        for(int i=0;i<env.config.players;i++){
           tokens.add(new ArrayList<Integer>()); 
        }
        this.availableSlots =new ArrayList<Integer>();
        for(int i=0;i<env.config.tableSize;i++){
            availableSlots.add(i);
        }
        
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card,slot);
        //remove available slots list
        if(!availableSlots.isEmpty() && availableSlots.contains(slot)){
            availableSlots.remove(availableSlots.indexOf(slot));
        }
    }
    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        //remove player token
        for (List<Integer> token : tokens) {
            if(token.contains(slot)){
                token.remove(Integer.valueOf(slot));
            }
        }
        //added to available slots list
        availableSlots.add(slot);
        //update UI
        int card = slotToCard[slot];
        cardToSlot[card] = null;
        slotToCard[slot] = null;
        env.ui.removeTokens(slot);
        env.ui.removeCard(slot);
        
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(!tokens.get(player).contains(slot)&& slotToCard[slot]!=null && tokens.get(player).size()!=maxToken){
            //add player token
            tokens.get(player).add(slot);
            //update UI
            env.ui.placeToken(player,slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if(tokens.get(player).contains(slot)){
            //remove player token
            int index=tokens.get(player).indexOf(slot);
            tokens.get(player).remove(index);
            //update UI
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
    }
    //added functions 
    public void SemAcquire(){//acquire semaphore
        try {
            sem.acquire();
        } catch (InterruptedException ignored) {}
    }
    //list of player tokens
    public List<Integer> getPlayerTokens(int player){
        return tokens.get(player);
    }
    //get Slot to card
    public int getSlotToCard(int slot){
        if(slotToCard[slot]==null){
            return notExits;
        }
        return slotToCard[slot];
    }
    //get card to slot
    public int getCardToSlot(int card){
        if(cardToSlot[card]==null){
            return notExits;
        }
        return cardToSlot[card];
    }
    //get available slot
    public int getAvailableSlot(){
        if(!availableSlots.isEmpty()){
            Collections.shuffle(availableSlots);
            int availableSlot=availableSlots.get(first);
            availableSlots.remove(first);
            return availableSlot;
        } 
        return notExits;
    }
    //return list of cards id on the table
    public List<Integer> currentCardonTable(){
        List<Integer>  currentCardonTable = new ArrayList<Integer>();
        for(int slot=0;slot<env.config.tableSize;slot++){
            int card = getSlotToCard(slot);
            if(card!=notExits){
                currentCardonTable.add(card);
            }
        }
        return currentCardonTable;
    } 
    //check if not exist set on the table
    public boolean notExistSetinTable(){
       return (env.util.findSets(currentCardonTable(), 1).size() == 0);
    }
}
