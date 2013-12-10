package eu.stratosphere.nephele.jobmanager.accumulators;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import eu.stratosphere.nephele.jobgraph.JobID;
import eu.stratosphere.nephele.services.accumulators.Accumulator;
import eu.stratosphere.nephele.services.accumulators.AccumulatorHelper;
import eu.stratosphere.nephele.types.StringRecord;

/**
 * This class manages the accumulators for different jobs. Either the jobs are
 * running and new accumulator results have to be merged in, or the jobs are no
 * longer running and the results shall be still available for the client or the
 * web interface. Accumulators for older jobs are automatically removed when new
 * arrive, based on a maximum number of entries.
 */
public class AccumulatorManager {
  
  // Map of accumulators belonging to recently started jobs
  private final Map<JobID, JobAccumulators> jobAccumulators = new HashMap<JobID, JobAccumulators>();

  private final LinkedList<JobID> lru = new LinkedList<JobID>();
  private int maxEntries;

  public AccumulatorManager(int maxEntries) {
    this.maxEntries = maxEntries;
  }

  public void processIncomingAccumulators(JobID jobID,
      Map<StringRecord, Accumulator<?, ?>> newAccumulators) {
    System.out.println("JobManager: Received accumulator result for job " + jobID.toString());
    System.out.println(AccumulatorHelper.getAccumulatorsFormated(newAccumulators));
    JobAccumulators jobAccumulators = this.jobAccumulators.get(jobID);
    if (jobAccumulators == null) {
      System.out.println("Register new accumulators");
      jobAccumulators = new JobAccumulators();
      this.jobAccumulators.put(jobID, jobAccumulators);
      cleanup(jobID);
    }
    jobAccumulators.processNew(newAccumulators);
  }

  private void cleanup(JobID jobId) {
    if (!lru.contains(jobId))
      lru.addFirst(jobId);
    if (lru.size() > this.maxEntries) {
      JobID toRemove = lru.removeLast();
      this.jobAccumulators.remove(toRemove);
    }
  }

  public Map<String, Accumulator<?, ?>> getJobAccumulators(JobID jobID) {
    JobAccumulators jobAccumulators = this.jobAccumulators.get(jobID);
    if (jobAccumulators == null) {
      return new HashMap<String, Accumulator<?, ?>>();
    }
    return jobAccumulators.getAccumulators();
  }
}
