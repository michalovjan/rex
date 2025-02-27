package org.jboss.pnc.rex.core.jobs;

import io.smallrye.mutiny.Uni;
import org.jboss.pnc.rex.core.RemoteEntityClient;
import org.jboss.pnc.rex.core.api.TaskController;
import org.jboss.pnc.rex.core.delegates.WithTransactions;
import org.jboss.pnc.rex.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.TransactionPhase;
import javax.enterprise.inject.spi.CDI;

public class InvokeStopJob extends ControllerJob {

    private static final TransactionPhase INVOCATION_PHASE = TransactionPhase.AFTER_SUCCESS;

    private final RemoteEntityClient client;

    private final TaskController controller;

    private static final Logger logger = LoggerFactory.getLogger(InvokeStopJob.class);

    @Override
    void beforeExecute() {}

    @Override
    void afterExecute() {}

    @Override
    void onException(Throwable e) {
        logger.error("STOP " + context.getName() + ": UNEXPECTED exception has been thrown.", e);
        Uni.createFrom().voidItem()
                .onItem().invoke((ignore) -> controller.fail(context.getName(), "STOP : System failure. Exception: " + e.toString()))
                .onFailure().invoke((throwable) -> logger.warn("STOP " + context.getName() + ": Failed to transition task to STOP_FAILED state. Retrying.", throwable))
                .onFailure().retry().atMost(5)
                .onFailure().recoverWithNull()
                .await().indefinitely();
    }

    public InvokeStopJob(Task task) {
        super(INVOCATION_PHASE, task);
        this.client = CDI.current().select(RemoteEntityClient.class).get();
        this.controller = CDI.current().select(TaskController.class, () -> WithTransactions.class).get();
    }

    @Override
    boolean execute() {
        logger.info("STOP {}: STOPPING", context.getName());
        client.stopJob(context);
        return true;
    }
}
