package com.amazon.ata.advertising.service.targeting;

import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicate;
import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;

import javax.lang.model.type.ExecutableType;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates TargetingPredicates for a given RequestContext.
 */
public class TargetingEvaluator {
    public static final boolean IMPLEMENTED_STREAMS = true;
    public static final boolean IMPLEMENTED_CONCURRENCY = true;
    private final RequestContext requestContext;
    private boolean allTruePredicates; //need to add this as an attribute

    /**
     * Creates an evaluator for targeting predicates.
     * @param requestContext Context that can be used to evaluate the predicates.
     */
    public TargetingEvaluator(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    /**
     * Evaluate a TargetingGroup to determine if all of its TargetingPredicates are TRUE or not for the given
     * RequestContext.
     * @param targetingGroup Targeting group for an advertisement, including TargetingPredicates.
     * @return TRUE if all of the TargetingPredicates evaluate to TRUE against the RequestContext, FALSE otherwise.
     */
    public TargetingPredicateResult evaluate(TargetingGroup targetingGroup) {
        allTruePredicates = true; //assume it's true initially
        List<TargetingPredicate> targetingPredicates = targetingGroup.getTargetingPredicates();
        ExecutorService executor = Executors.newCachedThreadPool();
        for (TargetingPredicate predicate: targetingPredicates) {
            executor.submit(
                    // This line starts a lambda expression that takes no arguments and returns void.
                    // run of this as the run() method in a class which implements Runnable interface
                    () -> {
                        // submit each evaluation
                        // if any of the evaluation is false
                        if (predicate.evaluate(this.requestContext)
                                .equals(TargetingPredicateResult.FALSE)) {
                            this.allTruePredicates = false;
                            // as soon as we have a false, it's evaluated as false overall
                            // we can exit and return that false value
                            executor.shutdownNow();
                        }
                    });
        }
        // This line starts a loop over each TargetingPredicate in the targetingPredicates list,
        // executing the code within the lambda expression for each predicate.
        // think of this as , for each predicate, do this ->
//        targetingPredicates.forEach(predicate -> executor.submit(
//                // This line starts a lambda expression that takes no arguments and returns void.
//                () -> {
//                    // submit each evaluation
//                    // if any of the evaluation is false
//                    if (predicate.evaluate(this.requestContext)
//                            .equals(TargetingPredicateResult.FALSE)) {
//                        this.allTruePredicates = false;
//                        // as soon as we have a false, it's evaluated as false overall
//                        // we can exit and return that false value
//                        executor.shutdownNow();
//                    }
//                }
//        ));

        // if allTruePredicates is false,
        // since the executor would've been shut down already,
        // we can just return false
        if (!allTruePredicates) {
            return TargetingPredicateResult.FALSE;
        } else {
            // if allTruePredicates remains true, we still shut down before returning the true evaluated value
            executor.shutdown();

            try {
                // Wait up to 5 seconds for the ExecutorService to finish everything.
                // If this method hits the timeout before everything has finished, it returns "false"/
                boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);
                System.out.println("Finished? " + finished);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            executor.shutdownNow();

            // old code without using ExecutorService
            // List<TargetingPredicate> targetingPredicates = targetingGroup.getTargetingPredicates();
//        boolean allTruePredicates = targetingPredicates.stream()
//                .allMatch(predicate -> predicate.evaluate(requestContext).isTrue());
//        // old code in a for-loop
//        for (TargetingPredicate predicate : targetingPredicates) {
//            TargetingPredicateResult predicateResult = predicate.evaluate(requestContext);
//            if (!predicateResult.isTrue()) {
//                allTruePredicates = false;
//                break;
//            }
//        }
            // if allTruePredicates is true, then return TargetingPredicateResult.TRUE
            // else return TargetingPredicateResult.FALSE
            return TargetingPredicateResult.TRUE;
        }
    }
}
