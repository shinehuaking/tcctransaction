package bean;

import api.*;
import reflect.ParticipantDetail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 使用序列号工具序列化后存储与仓库中，用于事务恢复
 */
public class Transaction implements Serializable {

    private static final long serialVersionUID = -1L;

    private TccTransactionXid xid;

    private TccTransactionStatus status;

    private TccTransactionType transactionType;

    private volatile int retriedCount = 0;

    private Date createTime = new Date();

    private Date lastUpdateTime = new Date();

    private List<Participant> participants = new ArrayList<Participant>();

    private long version = 1;

    public Transaction(){

    }

    public Transaction(TccTransactionType transactionType) {
        this.xid = new TccTransactionXid();
        this.status = TccTransactionStatus.TRYING;
        this.transactionType = transactionType;
    }

    public Transaction(TccTransactionContext context) {
        this.xid = context.getXid();
        this.status = TccTransactionStatus.TRYING;
        this.transactionType = TccTransactionType.BRANCH;
    }

    //添加参与者
    public void addParticipant(Participant participant){
        participants.add(participant);
    }

    public TccTransactionXid getXid() {
        return xid;
    }

    public void setXid(TccTransactionXid xid) {
        this.xid = xid;
    }

    public TccTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TccTransactionStatus status) {
        this.status = status;
    }

    public int getRetriedCount() {
        return retriedCount;
    }

    public void setRetriedCount(int retriedCount) {
        this.retriedCount = retriedCount;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public TccTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TccTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    //提交
    public void commit() {

        for (Participant participant : participants) {
            participant.commit();
        }
    }

    //回滚
    public void rollback() {

        for (Participant participant : participants) {
            participant.rollback();
        }
    }

    public void addRetriedCount() {
        this.retriedCount++;
    }

    public void resetRetriedCount(int retriedCount) {
        this.retriedCount = retriedCount;
    }

}
