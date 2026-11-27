package ph.printshop.pos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native production policy. Payment state is derived from the linked financial ledger,
 * never editable by a production screen. This class itself makes no database writes. */
final class ProductionWorkflow {
    private ProductionWorkflow(){}
    enum Status { QUOTATION, AWAITING_APPROVAL, PENDING, IN_PRODUCTION, QUALITY_CHECKING, READY_FOR_PICKUP, COMPLETED, CANCELLED }
    enum Payment { UNPAID, PARTIAL, PAID, REFUNDED, VOIDED }
    static final class FinancialState {
        final long total,balance;
        final boolean linked;
        final Payment payment;
        FinancialState(long total,long balance,boolean linked,Payment payment){
            if(total<0||balance<0||balance>total||payment==null)throw new IllegalArgumentException("Invalid job payment state");
            switch(payment){
                case UNPAID: if(total==0||balance!=total)throw new IllegalArgumentException("Invalid unpaid balance");break;
                case PARTIAL: if(balance<=0||balance>=total||!linked)throw new IllegalArgumentException("Invalid partial balance");break;
                case PAID: if(balance!=0||(!linked&&total>0))throw new IllegalArgumentException("Invalid paid balance");break;
                case REFUNDED: case VOIDED:
                    if(!linked||balance!=0)throw new IllegalArgumentException("Invalid reversed sale");break;
            }
            this.total=total;this.balance=balance;this.linked=linked;this.payment=payment;
        }
        boolean reversed(){return payment==Payment.REFUNDED||payment==Payment.VOIDED;}
    }
    static List<Status> allowed(Status current,FinancialState financial){
        if(current==null||financial==null)throw new IllegalArgumentException("Job state required");
        List<Status> next=new ArrayList<>();
        if(financial.reversed())return Collections.unmodifiableList(next);
        switch(current){
            case QUOTATION: next.add(Status.AWAITING_APPROVAL);break;
            case AWAITING_APPROVAL:
                next.add(Status.QUOTATION);
                if(financial.linked||financial.total==0)next.add(Status.PENDING);
                break;
            case PENDING: next.add(Status.IN_PRODUCTION);break;
            case IN_PRODUCTION: next.add(Status.QUALITY_CHECKING);break;
            case QUALITY_CHECKING: next.add(Status.IN_PRODUCTION);next.add(Status.READY_FOR_PICKUP);break;
            case READY_FOR_PICKUP:
                next.add(Status.QUALITY_CHECKING);
                if(financial.payment==Payment.PAID&&financial.balance==0&&(financial.linked||financial.total==0))next.add(Status.COMPLETED);
                break;
            case COMPLETED: case CANCELLED: break;
        }
        if((current==Status.QUOTATION||current==Status.AWAITING_APPROVAL||current==Status.PENDING)&&
            !financial.linked&&(financial.payment==Payment.UNPAID||financial.total==0))next.add(Status.CANCELLED);
        return Collections.unmodifiableList(next);
    }
    static void requireTransition(Status current,Status target,FinancialState financial){
        if(target==null||!allowed(current,financial).contains(target))
            throw new IllegalStateException("Invalid production transition or outstanding payment");
    }
    /** A ledger reversal is not an operator-requested production transition. */
    static Status afterReversal(Status current){
        if(current==null)throw new IllegalArgumentException("Job state required");
        return current==Status.QUOTATION||current==Status.AWAITING_APPROVAL||current==Status.PENDING?Status.CANCELLED:current;
    }
}
