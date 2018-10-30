import jade.core.Agent;

public class BookBuyerAgent extends Agent {

    protected void setup(){
        System.out.println("Hello! Buyer" + getAID().getName() +" is ready");
    }
}
