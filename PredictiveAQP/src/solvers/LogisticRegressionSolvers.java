package solvers;

import static java.lang.System.out;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import dataextraction.ProsperDataExtractor;
import dataextraction.CensusDataExtractor;
import dataextraction.LendingClubDataExtractor;
import dataextraction.MarketingDataExtractor;
import utils.PerformanceAnalysis;
import weka.classifiers.functions.Logistic;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class LogisticRegressionSolvers {
	
	/**
	 * For the loan files : 
	 * Preprocessing before calling this function:
	 * Remove top line of both csv files ("Notes ..."), so the new top line should be the column names.
	 * Remove occurrences of "" except those between commas ("" between commas means an empty string attribute value,
	 * but some of the other string values contain spurious pairs of adjacent double quotes, that may mess up csv
	 * parsing. Remove these, by say, replacing [^,]\"\" by a , and \"\"[^,] by a.). In addition, if running out of 
	 * heap space because of large number of (useless) tuples with an IGNORELOANSTATUS, then use regexp replaces to
	 * remove those lines. (replace "^.+\"Current\".+$" by "", etc, and then replace \n\n\n by \n, etc). Remove % signs 
	 * so attributes like interest rate are interpreted are numeric.
	 * 
	 * Then, combine the 2 csv files (remove top line of one of them, and append it to the other.)
	 * This function reads data from the combined csv file, filters some attributes, renames class variables, and 
	 * outputs an arff file.
	 * 
	 * Then execute :
	 *  For Lending Club,
	    writeImprovedFile (LendingClubDataExtractor.COMBINEDFILELOCATION, LendingClubDataExtractor.IMPROVEDFILELOCATION, LendingClubDataExtractor.TARGET, 
				LendingClubDataExtractor.IGNORETARGET, LendingClubDataExtractor.GOODTARGET, LendingClubDataExtractor.BADTARGET);
		And for Prosper 
		writeImprovedFile (ProsperDataExtractor.FILELOCATION, ProsperDataExtractor.ARFFFILELOCATION, ProsperDataExtractor.TARGET, 
				ProsperDataExtractor.IGNORETARGET, ProsperDataExtractor.GOODTARGET, ProsperDataExtractor.BADTARGET);
		
	 * For Marketing Data, execute : 
		writeImprovedFile (MarketingDataExtractor.FILELOCATION, MarketingDataExtractor.ARFFFILELOCATION, MarketingDataExtractor.TARGET,
				MarketingDataExtractor.IGNORETARGET, MarketingDataExtractor.GOODTARGET, MarketingDataExtractor.BADTARGET);
					
	 * For Census Data, execute :
		writeImprovedFile (CensusDataExtractor.FILELOCATION, CensusDataExtractor.ARFFFILELOCATION, CensusDataExtractor.TARGET,
				CensusDataExtractor.IGNORETARGET, CensusDataExtractor.GOODTARGET, CensusDataExtractor.BADTARGET);
				
	 * After executing the code (for all three), we need to manually go to the arff file, and modify the class variable 
	 * by replacing its values set with {good, bad} (remove duplicates, etc). Also make sure the class values listed in the values set
	 * are {good,bad} in that order, and reverse them if they aren't. The first of these is the UDF value we want to select.
	 * @throws Exception 
	 */
	public static void writeImprovedFile (String combinedFileLocation, String improvedFileLocation, Integer target, Set<String> ignoreTargets, 
			Set<String> goodTargets, Set<String> badTargets) throws Exception {
		out.println(combinedFileLocation);
		DataSource source = new DataSource(combinedFileLocation);
		Instances instances;
		instances = source.getDataSet();
		instances.setClassIndex(target);
		
		for (String good : goodTargets) {
			int i = instances.attribute(target ).indexOfValue(good);
			instances.renameAttributeValue(target , i, "good");
		}
		for (String bad : badTargets) {
			int i = instances.attribute(target ).indexOfValue(bad);
			instances.renameAttributeValue(target , i, "bad");
		}
		for (String ignore : ignoreTargets) {
			int i = instances.attribute(target ).indexOfValue(ignore);
			if (i >= 0) {
				instances.renameAttributeValue(target , i, "missing");
			}
		}

		for (int i = 0; i < instances.numInstances(); i++) {
			if (instances.instance(i).stringValue(target).equals("missing")) {
				instances.instance(i).setClassMissing();
			}
		}
		/*
		 * The three lines below are kind of arbitrary. They were introduced for the loan data, to get rid of a lot of 
		 * worthless attributes. And they don't affect the other datasets (<= 21 attributes).
		 */
		for (int i = instances.numAttributes() - 1; i >= 20; i--) {
			//System.out.println(i);
			if (instances.classIndex() == i) {
				continue;
			}
			instances.deleteAttributeAt(i);
		}
		instances.deleteWithMissingClass();
		PrintWriter outFile = new PrintWriter(new FileWriter(improvedFileLocation));
		outFile.println(instances.toString());
		outFile.close();
	}
	
	/**
	 * Populates the empty inputed sizes, selectivities and positive/negativeSamples maps, for groups made 
	 * using logistic regression. Uses logistic regression on 11 attributes and trainFraction fraction of 
	 * tuples as training data, to estimate probabilities for each tuple, then divides tuples into numClasses 
	 * groups based on the probabilities, and returns the size and actual selectivity (not the average 
	 * probability given by the logistic regressor) of each group. positive and negative Samples is populated to
	 * contain info about training Data (how many positive and negative examples per group are in training data).
	 * scheme parameter :
	 * interval-length: makes intervals of equals length (e.g. for numClasses = 10, intervals are [0,0.1), [0.1,0.2), etc)
	 * bucket-size: makes intervals so that bucket sizes for training data are approximately equal.
	 */
	public static void logisticRegressionGroups (String inputFile, Integer target, Map<String, Double> sizes, Map<String, Double> selectivities, 
			Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples, int numClasses, Double trainFraction, 
			String scheme) throws Exception {
		DataSource source = new DataSource(inputFile);
		Instances instances = source.getDataSet(target);
		for (int i = 0; i < instances.numAttributes(); i++) {
			Attribute attr = instances.attribute(i);
			if (attr.numValues() > 50) {
				instances.deleteAttributeAt(i);
				i--;
			}
		}
		instances.randomize(new Random());
		Instances trainInstances = new Instances(instances, (int)(trainFraction*instances.numInstances()));
		Instances testInstances = new Instances(instances, (int)((1-trainFraction)*instances.numInstances()));
		for (int i = 0; i < instances.numInstances(); i++) {
			if (Math.random() < trainFraction) {
				trainInstances.add(instances.instance(i));
			} else {
				testInstances.add(instances.instance(i));
			}
		}
		
		Logistic logistic = new Logistic();
		logistic.buildClassifier(trainInstances);
		
		String[] classNames = new String[numClasses];
		Double[] classLowerBounds = new Double[numClasses];
		if (scheme.equals("bucket-size")) {
			List<Double> probabilities = new ArrayList<Double>();
			for (int i = 0; i < trainInstances.numInstances(); i++) {
				final Instance instance = trainInstances.instance(i);
				double[] distribution = logistic.distributionForInstance(instance);
				if (distribution[0] > 1 - 0.5/numClasses) {
					distribution[0] = 1 - 0.5/numClasses; //To prevent the value 1.0
				}
				probabilities.add(distribution[0]);
			}
			Collections.sort(probabilities);
			int numProbs = probabilities.size();
			for (int i=0; i < numClasses; i++) {
				classLowerBounds[i] = probabilities.get(i*numProbs/numClasses);
				classNames[i] = classLowerBounds[i].toString();
			}
		} else if (scheme.equals("interval-length")) {
			for (Integer i = 0; i < numClasses; i++) {
				Double classNum = i.doubleValue();
				classNum /= numClasses;
				classLowerBounds[i] = classNum;
				classNames[i] = classNum.toString();
			}
		} else {
			throw new IllegalArgumentException("scheme parameter not recognized");
		}
		
		for (int i = 0; i < numClasses; i++) {
			sizes.put(classNames[i], 0.0);
			selectivities.put(classNames[i], 0.0);
			positiveSamples.put(classNames[i], 0);
			negativeSamples.put(classNames[i], 0);
		}
		
		for (int i = 0; i < trainInstances.numInstances(); i++) {
			final Instance instance = trainInstances.instance(i);
			String className = "";
			double[] distribution = logistic.distributionForInstance(instance);
			for (int classNum = numClasses - 1 ; classNum >=0; classNum--) {
				if (distribution[0] >= classLowerBounds[classNum]) {
					className = classNames[classNum];
					break;
				}
			}
			if (instance.classValue() < 0.5) {
				positiveSamples.put(className, 1 + positiveSamples.get(className));
			} else {
				negativeSamples.put(className, 1 + negativeSamples.get(className));
			}
		}
		
		for (int i = 0; i < testInstances.numInstances(); i++) {
			final Instance instance = testInstances.instance(i);
			String className = classNames[0];
			double[] distribution = new double[2];
			try{
				distribution = logistic.distributionForInstance(instance);
			} catch (Exception e) {
				if (instance == null) {
					System.out.println("nuLL");
				}
			}
			for (int classNum = numClasses - 1 ; classNum >=0; classNum--) {
				if (distribution[0] >= classLowerBounds[classNum]) {
					className = classNames[classNum];
					break;
				}
			}
			sizes.put(className, sizes.get(className) + 1);
			if (instance.classValue() < 0.5) {
				selectivities.put(className, selectivities.get(className) + 1);
			}
		}
		for (String className : sizes.keySet()) {
			selectivities.put(className, selectivities.get(className) / sizes.get(className));
		}
	}
	
	public static void main(String[] argv) throws Exception {
		if(1!=2)return;
		Double trainFraction = 1.0/10;
		final Integer numClasses = 10;
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
		Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
		logisticRegressionGroups(CensusDataExtractor.ARFFFILELOCATION, CensusDataExtractor.TARGET, sizes, selectivities, positiveSamples, negativeSamples, numClasses, trainFraction, "bucket-size");
		if(1!=2)return;
		for (trainFraction = 0.02; trainFraction < 0.03; trainFraction += 0.02) {
			logisticRegressionGroups(CensusDataExtractor.ARFFFILELOCATION, CensusDataExtractor.TARGET, sizes, selectivities, positiveSamples, negativeSamples, numClasses, trainFraction, "bucket-size");
			System.out.println(sizes.toString());
			//if(1!=2)return;
			System.out.println("Fraction: " + trainFraction);
			System.out.println(sizes.toString());
			System.out.println(selectivities.toString());
			System.out.println(positiveSamples.toString());
			System.out.println(negativeSamples.toString());
		}
	}
}
