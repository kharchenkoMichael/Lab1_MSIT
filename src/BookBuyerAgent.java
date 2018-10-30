import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class BookBuyerAgent extends Agent {

    private ArrayList<String> targetBooksTitles = new ArrayList<String>();

    private AID[] sellerAgents;

    protected void setup() {
        System.out.println("Hello! Buyer" + getAID().getName() + " is ready");

        Object[] args = getArguments();

        if (args == null) {
            System.out.println("No target book title specified");
            doDelete();
            return;
        }

        for (int i = 0; i < args.length; i++)
            targetBooksTitles.add((String) args[i]);

        addBehaviour(new TickerBehaviour(this, 60000) {
            @Override
            protected void onTick() {
                SearchAgentSeller(myAgent);

                for (String targetBookTitle : targetBooksTitles) {
                    System.out.println("Trying to buy " + targetBookTitle);
                    myAgent.addBehaviour(new RequestPerformer(targetBookTitle));
                }
            }
        });
    }

    protected void takeDown() {
        System.out.println("Buyer-agent "+getAID().getName()+" terminating.");
    }

    private void SearchAgentSeller(Agent myAgent) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("book-selling");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(myAgent, template);
            System.out.println("Found the following seller agents:");
            sellerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                sellerAgents[i] = result[i].getName();
                System.out.println(sellerAgents[i].getName());
            }
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    public class RequestPerformer extends Behaviour {

        private String targetBookTitle;
        private MessageTemplate mt;
        private int step = 0;

        private AID bestSeller;
        private int bestPrice;
        private int repliesCnt = 0;

        public RequestPerformer(String bookTitle) {
            targetBookTitle = bookTitle;
        }

        @Override
        public void action() {
            switch (step) {
                case 0:
                    SendRequestToSellers();
                    break;
                case 1:
                    FindBestSeller();
                    break;
                case 2:
                    BuyBookFromBestSeller();
                    break;
                case 3:
                    TakeResponceBook();
                    break;
            }
        }

        @Override
        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println("Attempt failed: "+targetBookTitle+" not available for sale");
            }
            return ((step == 2 && bestSeller == null) || step == 4);
        }

        private void SendRequestToSellers(){
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

            for (int i = 0; i < sellerAgents.length; ++i)
                cfp.addReceiver(sellerAgents[i]);

            cfp.setContent(targetBookTitle);
            cfp.setConversationId("book-trade");
            cfp.setReplyWith("cfp" + System.currentTimeMillis());
            myAgent.send(cfp);

            mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                    MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
            step++;
        }

        private void FindBestSeller(){
            ACLMessage reply = myAgent.receive(mt);
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.PROPOSE) {
                    int price = Integer.parseInt(reply.getContent());
                    if (bestSeller == null || price < bestPrice) {
                        bestPrice = price;
                        bestSeller = reply.getSender();
                    }
                }
                repliesCnt++;

                if (repliesCnt >= sellerAgents.length)
                    step++;
            }
            else {
                block();
            }
        }

        private void BuyBookFromBestSeller() {
            ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            order.addReceiver(bestSeller);
            order.setContent(targetBookTitle);
            order.setConversationId("book-trade");
            order.setReplyWith("order"+System.currentTimeMillis());
            myAgent.send(order);

            mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                    MessageTemplate.MatchInReplyTo(order.getReplyWith()));
            step++;
        }

        private void TakeResponceBook(){
            ACLMessage reply = myAgent.receive(mt);
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.INFORM) {
                    System.out.println(targetBookTitle+" successfully purchased from agent "+reply.getSender().getName());
                    System.out.println("Price = "+bestPrice);
                    myAgent.doDelete();
                }
                else
                    System.out.println("Attempt failed: requested book already sold.");

                step = 4;
            }
            else {
                block();
            }
        }
    }
}
