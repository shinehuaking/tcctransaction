package manager;

import api.Participant;
import api.TccTransactionContext;
import api.TccTransactionStatus;
import api.TccTransactionType;
import bean.Transaction;
import com.alibaba.fastjson.JSON;
import exception.TccCancelingException;
import exception.TccConfirmingException;
import exception.TccSystemException;
import exception.TccTransactionNotFoundException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import repository.JdbcTransactionRepository;
import repository.TransactionRepository;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;


/**
 * 事务管理器，提供事务的获取、发起、提交、回滚，添加参与者的新增等方法。
 *
 */
public class TccTransactionManager {

    static final Logger logger = Logger.getLogger(TccTransactionManager.class.getSimpleName());

    //事务的持久化存储
    private TransactionRepository transactionRepository;

    //异步处理
    private ExecutorService executorService;

    private static final ThreadLocal<LinkedList<Transaction>> CURRENT = new ThreadLocal<LinkedList<Transaction>>();

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    /**
     * 开启一个根事务
     */
    public Transaction begin() {

        Transaction transaction = new Transaction(TccTransactionType.ROOT);
        //持久化方法，用于事务恢复
        transactionRepository.create(transaction);
        //注册当前线程的事务，存入ThreadLocal
        registerTransaction(transaction);

        return transaction;
    }

    /**
     * 开启一个新的分支事务,与根事务使用同一个xid(在当前以存在事务的基础上)
     */
    public Transaction branchNewBegin(TccTransactionContext context) {

        Transaction transaction = new Transaction(context);
        //持久化方法，用于事务恢复
        transactionRepository.create(transaction);
        //注册当前线程的事务，存入ThreadLocal
        registerTransaction(transaction);
        return transaction;
    }

    public Transaction branchExistBegin(TccTransactionContext transactionContext) throws TccTransactionNotFoundException {
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        if (transaction != null) {
            transaction.setStatus(transactionContext.getStatus());
            registerTransaction(transaction);
            return transaction;
        }

        //处理异常
        throw new TccTransactionNotFoundException();
    }
    /**
     * 提交事务，从ThreadLocal中获取事务 更改状态 执行注解中的commit方法
     *
     *
     * @param asyncCommit
     */
    public void commit(Boolean asyncCommit) {

        final Transaction transaction = this.getCurrentTransaction();
        transaction.setStatus(TccTransactionStatus.CONFIRMING);
        transactionRepository.update(transaction);

        if (asyncCommit) {

            //异步提交事务
            //异步超时问题
            try {

                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        commitTransaction(transaction);
                    }
                });

                return;
            }catch (Throwable commitException){

                logger.warn("transaction async commit failed, recovery job will try to commit later.", commitException);
                throw new TccConfirmingException(commitException);
            }


        }

        commitTransaction(transaction);

    }

    /**
     * 回滚事务，从ThreadLocal中获取当前事务 更改状态 执行注解中的rollback方法
     *
     *
     * @param asyncCancel
     */
    public void rollback(Boolean asyncCancel) {

        final Transaction transaction = this.getCurrentTransaction();
        transaction.setStatus(TccTransactionStatus.CANCELLING);
        transactionRepository.update(transaction);

        if (asyncCancel) {

            try {


                //异步回滚事务
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        rollbackTransaction(transaction);
                    }
                });
                return;
            }catch (Throwable rollbackException){

                logger.warn("transaction async rollback failed, recovery job will try to rollback later.", rollbackException);
                throw new TccConfirmingException(rollbackException);
            }
        }

        rollbackTransaction(transaction);
    }

    public void syncTransaction() {

        final Transaction transaction = getCurrentTransaction();
        /**
         * update the transaction to persist the participant context info
         */
        transactionRepository.update(transaction);
    }

    /**
     * 将ThreadLocal中存储的事务清除 pop
     */
    public void cleanAfterCompletion(Transaction transaction) {

        if (isExistTransaction() && transaction != null) {

            Transaction currentTransaction = this.getCurrentTransaction();

            if (currentTransaction == transaction) {
                CURRENT.get().pop();
            }
        }


    }

    private void commitTransaction(Transaction transaction) {

        try {

            transaction.commit();
            //transactionRepository.delete(transaction);
        } catch (Throwable commitException) {

            logger.error("提交事务异常", commitException);
            throw new TccConfirmingException(commitException);
        }
    }

    private void rollbackTransaction(Transaction transaction) {

        try {

            transaction.rollback();
            //transactionRepository.delete(transaction);
        } catch (Throwable rollbackException) {

            logger.error("回滚事务异常", rollbackException);
            throw new TccCancelingException(rollbackException);
        }
    }

    /**
     * 将事务存储到当前线程中ThreadLocal中
     *
     * @param transaction
     */
    private void registerTransaction(Transaction transaction) {

        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<Transaction>());
        }

        CURRENT.get().push(transaction);
    }

    /**
     * 获取当前事务
     *
     * @return
     */
    public Transaction getCurrentTransaction() {

        if (isExistTransaction()) {
            return CURRENT.get().peek();
        }
        return null;
    }

    /**
     * 当前是否存在事务
     *
     * @return
     */
    public boolean isExistTransaction() {
        Deque<Transaction> transactions = CURRENT.get();
        return transactions != null && !transactions.isEmpty();
    }

    /**
     * 添加事务参与者
     * @param participant
     */
    public void enlistParticipant(Participant participant) {
        Transaction transaction = this.getCurrentTransaction();
        transaction.addParticipant(participant);
        System.out.println("==================================");
        System.out.println(JSON.toJSONString(participant));
        System.out.println("==================================");
        transactionRepository.update(transaction);
    }
}
