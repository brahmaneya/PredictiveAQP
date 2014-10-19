package utils;

import static java.lang.System.out;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import solvers.ExpectationSolvers;
import solvers.LogisticRegressionSolvers;
import solvers.ProbabilisticSolvers;

public class Experiments {

	public static void performanceComparison(Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Double retrieveCost, Double evaluateCost) {
		Map<String, Double> retrieve = new HashMap<String, Double>();
		Map<String, Double> evaluate = new HashMap<String, Double>();
		Double numRetrieved;
		Double numEvaluated;
		ExpectationSolvers.fullEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
		numRetrieved = PerformanceAnalysis.retrieveCost(sizes, retrieve);
		numEvaluated = PerformanceAnalysis.evaluateCost(sizes, evaluate);
		out.println("fullEvaluate\t" + numRetrieved.toString() + "\t" + numEvaluated.toString());
				
		retrieve = new HashMap<String, Double>();
		evaluate = new HashMap<String, Double>();
		ExpectationSolvers.greedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
		numRetrieved = PerformanceAnalysis.retrieveCost(sizes, retrieve);
		numEvaluated = PerformanceAnalysis.evaluateCost(sizes, evaluate);
		//out.println(sizes.toString());
		//out.println(retrieve.toString());
		//out.println(evaluate.toString());
		out.println("greedyEvaluate\t" + numRetrieved.toString() + "\t" + numEvaluated.toString());
				
		retrieve = new HashMap<String, Double>();
		evaluate = new HashMap<String, Double>();
		ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
		numRetrieved = PerformanceAnalysis.retrieveCost(sizes, retrieve);
		numEvaluated = PerformanceAnalysis.evaluateCost(sizes, evaluate);
		out.println("sizesKnownEvaluate\t" + numRetrieved.toString() + "\t" + numEvaluated.toString());
		
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		Double totalSize = 0.0;
		for (String key : sizes.keySet()) {
			totalSize += sizes.get(key);
		}
		Double num = 0.05 * totalSize / sizes.keySet().size(); // Choose num to sample 5% of the tuples
		out.println(num*sizes.keySet().size());
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		final Integer numIters = 50;
		Double retrieves = 0.0;
		Double evaluates = 0.0;
		for (Integer iter = 0; iter < numIters; iter++) {
			Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
			Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
			retrieve = new HashMap<String, Double>();
			evaluate = new HashMap<String, Double>();
			//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
			//ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
			Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, "constant", num);
			PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
			try {
				ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
			} catch (Exception e) {
				PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
				iter--;
				continue;
			}
			Map<String, Double> stats = PerformanceAnalysis.findStats(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
			retrieves += stats.get("expectedRetrieves");
			evaluates += stats.get("expectedEvaluates");
			PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
		}
		out.println("sampling Scheme\t" + retrieves/numIters + "\t" + evaluates/numIters);
	}
	
	public static void constraintsCost(Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Double retrieveCost, Double evaluateCost) throws IOException {
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		
		PrintWriter pw;
		final Integer numIters = 50;
		
		for (Double num = 0.5; num < 5.0; num += 1.0) {
			pw = new PrintWriter(new FileWriter("Cost_alphabeta_"+num));
			for (alpha = 0.2; alpha < 0.95; alpha += 0.1) {
				for (beta = 0.2; beta <0.95; beta += 0.1) {
					Double retrieves = 0.0;
					Double evaluates = 0.0;
					for (Integer iter = 0; iter < numIters; iter++) {
						Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
						Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
						Map<String, Double> retrieve = new HashMap<String, Double>();
						Map<String, Double> evaluate = new HashMap<String, Double>();
						//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
						//ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
						Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, "two-third-power", alpha*num);
						PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
						try {
							ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
						} catch (Exception e) {
							PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
							iter--;
							continue;
						}
						Map<String, Double> stats = PerformanceAnalysis.findStats(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
						retrieves += stats.get("expectedRetrieves");
						evaluates += stats.get("expectedEvaluates");
						PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
					}
					pw.println(num + "\t" + alpha + "\t" + beta + "\t" + retrieves/numIters + '\t' + evaluates/numIters);
					out.println("constraintCost done with " + alpha + "\t" + beta);
				}
			}
			pw.close();
		}
	}
	
	public static void logisticRegression (String inputFile, Integer target, Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Double retrieveCost, Double evaluateCost) throws Exception {
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		sizes = new HashMap<String, Double>();
		selectivities = new HashMap<String, Double>();
		final int numClasses = 10;
		Double trainFraction = 0.01;
		PrintWriter pw = new PrintWriter(new FileWriter("Cost_logisticGroups_bucketsize"+trainFraction));
		Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
		Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
		Map<String, Integer> targetPositiveSamples = new HashMap<String, Integer>();
		Map<String, Integer> targetNegativeSamples = new HashMap<String, Integer>();
		Integer trainingEvaluates = 0;
		Integer trainingRetrieves = 0;
		final Integer numIters = 50;
		LogisticRegressionSolvers.logisticRegressionGroups(inputFile, target, sizes, selectivities, positiveSamples, negativeSamples, numClasses, trainFraction, "bucket-size");
		for (String key : sizes.keySet()) {
			//////// This depends on whether we should count examples in the training set towards selectivities. 
			trainingEvaluates += positiveSamples.get(key) + negativeSamples.get(key);
			trainingRetrieves += positiveSamples.get(key) + negativeSamples.get(key); 
			positiveSamples.put(key, 0);
			negativeSamples.put(key, 0);
			////////
			targetPositiveSamples.put(key, positiveSamples.get(key));
			targetNegativeSamples.put(key, negativeSamples.get(key));
		}
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		for (Double num = 0.5; num < 15.0; num += 0.5) {
			Double retrieves = 0.0;
			Double evaluates = 0.0;
			for (Integer iter = 0; iter < numIters; iter++) {
				Map<String, Double> retrieve = new HashMap<String, Double>();
				Map<String, Double> evaluate = new HashMap<String, Double>();
				//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				//ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				for (int rep = 0; rep < 1; rep++) {
					Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, "two-third-power", num);
					PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);							
				}
				//System.out.println(positiveSamples.toString());
				try {
					ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
				} catch (Exception e) {
					PerformanceAnalysis.partialUnsample(sizes, positive, negative, null, positiveSamples, negativeSamples, targetPositiveSamples, targetNegativeSamples);
					iter--;
					continue;
				}
				Map<String, Double> stats = PerformanceAnalysis.findStats(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
				retrieves += stats.get("expectedRetrieves");
				evaluates += stats.get("expectedEvaluates");
				retrieves += trainingRetrieves;
				evaluates += trainingEvaluates;
				PerformanceAnalysis.partialUnsample(sizes, positive, negative, null, positiveSamples, negativeSamples, targetPositiveSamples, targetNegativeSamples);
				
			}
			pw.println(num + "\t" + retrieves/numIters + '\t' + evaluates/numIters);
			out.println(num + "\t" + retrieves/numIters + '\t' + evaluates/numIters);
			out.println("done with "+num);
		}
		pw.close();
	}
	
	public static void adaptiveSampling (Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Double retrieveCost, Double evaluateCost, List<Integer> predictors) throws Exception {
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		
		PrintWriter pw;
		final Integer numIters = 50;
		String predictorString = "";
		
		for (Integer pred : predictors) {
			predictorString = predictorString + "-" + pred;
		}
		pw = new PrintWriter(new FileWriter("Cost_"+predictorString+"_two-third-power-selectivity"));
		for (Double num = 0.5; num < 15.0; num += 1) {
			Double retrieves = 0.0;
			Double evaluates = 0.0;
			for (Integer iter = 0; iter < numIters; iter++) {
				Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
				Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
				Map<String, Double> retrieve = new HashMap<String, Double>();
				Map<String, Double> evaluate = new HashMap<String, Double>();
				//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				//ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				for (int rep = 0; rep < 10; rep++) {
					Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, "two-third-power-selectivity", num);
					PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
				}
				
				try {
					ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
				} catch (Exception e) {
					PerformanceAnalysis.unsample(sizes, positive, negative, null, positiveSamples, negativeSamples);
					iter--;
					continue;
				}
				Map<String, Double> stats = PerformanceAnalysis.findStats(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
				retrieves += stats.get("expectedRetrieves");
				evaluates += stats.get("expectedEvaluates");
				PerformanceAnalysis.unsample(sizes, positive, negative, null, positiveSamples, negativeSamples);
			}
			pw.println(num + "\t" + retrieves/numIters + '\t' + evaluates/numIters);
			out.println("done with "+num);
		}
		pw.close();
		
	}
	
	public static void samplingCost (Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Double retrieveCost, Double evaluateCost, List<Integer> predictors,
			String samplingScheme) throws Exception {
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		PrintWriter pw;
		final Integer numIters = 50;
		
		String predictorString = "";
		for (Integer pred : predictors) {
			predictorString = predictorString + "-" + pred;
		}
		
		pw = new PrintWriter(new FileWriter("Cost_"+predictorString+"_"+samplingScheme));
		Double numMin = 100.0, numMax = 5000.0, numInc = 200.0;
		if (!samplingScheme.equals("constant")) {
			numMin = 0.5;
			numMax = 15.0;
			numInc = 1.0;
		}
		for (Double num = numMin; num < numMax; num += numInc) {
			Double retrieves = 0.0;
			Double evaluates = 0.0;
			for (Integer iter = 0; iter < numIters; iter++) {
				Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
				Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
				Map<String, Double> retrieve = new HashMap<String, Double>();
				Map<String, Double> evaluate = new HashMap<String, Double>();
				//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				//ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, samplingScheme, num);
				PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
				try {
					ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
				} catch (Exception e) {
					PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
					iter--;
					continue;
				}
				Map<String, Double> stats = PerformanceAnalysis.findStats(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
				retrieves += stats.get("expectedRetrieves");
				evaluates += stats.get("expectedEvaluates");
				PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
			}
			pw.println(num + "\t" + retrieves/numIters + '\t' + evaluates/numIters);
			out.println(num + "\t" + retrieves/numIters + '\t' + evaluates/numIters);
		}
		pw.close();
	}
	
	public static void samplingAccuracy (Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Double retrieveCost, Double evaluateCost) throws Exception {
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		Double num = 0.5;
		PrintWriter pw;
		final Integer numIters = 500;

		pw = new PrintWriter(new FileWriter("PrecRecall_errorsChebyshev"));
		for (rho = 0.5; rho < 0.95; rho += 0.05) {
			Double precision = 0.0;
			Double recall = 0.0;
			for (Integer iter = 0; iter < numIters; iter++) {
				Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
				Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
				Map<String, Double> retrieve = new HashMap<String, Double>();
				Map<String, Double> evaluate = new HashMap<String, Double>();
				//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				//ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, "two-third-power", num);
				PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
				try {
					ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
				} catch (Exception e) {
					PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
					iter--;
					continue;
				}
				
				Map<String, Double> avgstats = PerformanceAnalysis.precisionRecallThresholdFraction(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples, alpha, beta, 10);
				precision += avgstats.get("precision");
				recall += avgstats.get("recall");
				PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
			}
			pw.println(rho + "\t" + precision/numIters + '\t' + recall/numIters);
			out.println("done with "+rho);
		}
		pw.close();
		
		pw = new PrintWriter(new FileWriter("PrecRecall_sizesKnown"));
		for (rho = 0.5; rho < 0.95; rho += 0.050) {
			Double precision = 0.0;
			Double recall = 0.0;
			for (Integer iter = 0; iter < numIters; iter++) {
				Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
				Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
				Map<String, Double> retrieve = new HashMap<String, Double>();
				Map<String, Double> evaluate = new HashMap<String, Double>();
				//ExpectationSolvers.biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
				Map<String, Integer> totalSamples = PerformanceAnalysis.generateSampleSizes(sizes, positiveSamples, negativeSamples, "two-third-power", num);
				/*
				PerformanceAnalysis.sample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
				try {
					ProbabilisticSolvers.errorsInSizesEvaluate(sizes, null, null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
				} catch (Exception e) {
					PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
					iter--;
					continue;
				}
				*/
				Map<String, Double> avgstats = PerformanceAnalysis.precisionRecallThresholdFraction(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples, alpha, beta, 10);
				precision += avgstats.get("precision");
				recall += avgstats.get("recall");
				PerformanceAnalysis.unsample(sizes, positive, negative, totalSamples, positiveSamples, negativeSamples);
			}
			pw.println(rho + "\t" + precision/numIters + '\t' + recall/numIters);
			out.println("done with "+rho);
		}
		pw.close();
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
