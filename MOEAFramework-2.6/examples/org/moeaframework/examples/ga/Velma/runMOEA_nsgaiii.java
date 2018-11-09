package org.moeaframework.examples.ga.Velma;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.algorithm.ReferencePointNondominatedSortingPopulation;
import org.moeaframework.core.EpsilonBoxDominanceArchive;
import org.moeaframework.core.Initialization;
import org.moeaframework.core.NondominatedSortingPopulation;
import org.moeaframework.core.PRNG;
import org.moeaframework.core.Problem;
import org.moeaframework.core.Selection;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variable;
import org.moeaframework.core.Variation;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.CrowdingComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.operator.RandomInitialization;
import org.moeaframework.core.operator.TournamentSelection;
import org.moeaframework.core.spi.OperatorFactory;
import org.moeaframework.util.TypedProperties;
import org.moeaframework.util.distributed.DistributedProblem;

import gov.epa.velmamoeaapi.CalibratorConfiguration;
import gov.epa.velmamoeaapi.CalibratorConfigurationLoadFailedException;
import gov.epa.velmamoeaapi.CalibratorMoeaOutputLocation;
import gov.epa.velmamoeaapi.CalibratorObjectiveFunction;
import gov.epa.velmamoeaapi.CalibratorVelmaExclusive;
import gov.epa.velmamoeaapi.CalibratorVelmaParameter;
import gov.epa.velmamoeaapi.CalibratorVelmaParameterFollow;
import gov.epa.velmamoeaapi.ICalibratorConfigurationRecord;
import gov.epa.velmautils.Utils;

public class runMOEA_nsgaiii {

    public static void main(String[] args) {
        /////////////////////////////////////////////
        //CURRENT HARD-CODED VALUES
        /////////////////////////////////////////////
        //
        //1. NSGA-II algorithm
        //2. The following Properties 
        //sbx.rate = 1.0
        //sbx.distributionIndex = 10.0
        //pm.rate = 1/numberOfVariables
        //pm.distributionIndex = 15.0 
        //3. Initialization Method = Random Initialization. 
        //4. PopulationSize = number of processors available. 
        //
        /////////////////////////////////////////////

        /////////////////////////////////////////////////////// 
        //      COMMAND LINE ARGUMENTS 
        /////////////////////////////////////////////////////// 
        //Set Seeds within COMMAND LINE ARGUMENTS
        //This is especially useful if you want to create island parallelization in the future. 
        PRNG.setSeed(Long.parseLong(args[0])); 


        //      Testing Change Population Size to 1. 
        //final int populationSize = 3;
        final int populationSize = Runtime.getRuntime().availableProcessors();


        System.out.println("Number of cores/population used in this run is" + populationSize);

        /////////////////////////////////////////////
        //Set up distributed VelmaProblem 
        /////////////////////////////////////////////
        ExecutorService executor = Executors.newFixedThreadPool(populationSize);

        final Problem problem = new DistributedProblem(new VelmaCalibWithConstraint(args[1]), executor);

        Initialization initialization = new Initialization() {
            public Solution[] initialize() {
                Solution[] result = new RandomInitialization(problem, populationSize).initialize();
                return result;
            }
        };
//
//        NondominatedSortingPopulation population = 
//                new NondominatedSortingPopulation();
//
        TournamentSelection selection = new TournamentSelection(2, 
                new ChainedComparator(
                        new ParetoDominanceComparator(),
                        new CrowdingComparator())
                );
//
        Properties properties = new Properties();
        properties.setProperty("sbx.rate", "0.9");
        properties.setProperty("sbx.distributionIndex", "10.0"); //previously 15.0
        properties.setProperty("pm.rate", Double.toString(1.0 / problem.getNumberOfVariables()) ); 
        properties.setProperty("pm.distributionIndex", "15.0"); // previously 20.0

        Variation variation = OperatorFactory.getInstance().getVariation(null, 
                new TypedProperties(properties), problem);

//        Currently using NSGA-II for the genetic algorithm.
//        NSGAII algorithm = new NSGAII(problem, population, null, selection, variation,
//                initialization);
        //Try NSGA-III algorithm
//        NSGAIII algorithm = new ReferencePointNondominatedSortingPopulation(int numberOfObjectives,
//                int divisions);
        ReferencePointNondominatedSortingPopulation refPop = new ReferencePointNondominatedSortingPopulation(VelmaCalibWithConstraint.getNumObjs(args[1])
                , 4);
        

        NSGAII algorithm = new NSGAII(problem, refPop,
                null, selection,
                 variation, initialization);
        
        while (!algorithm.isTerminated() && (algorithm.getNumberOfEvaluations() < Double.MAX_VALUE)) {
            algorithm.step();

            //Get calconfig csv filename. 
            String startupFullName = args[1];

            //Get output from algorithm. 
            NondominatedSortingPopulation currPop = algorithm.getPopulation();


            ///////////////////////////////////////////////////////////////////////////////////////////
            ///////////////////////////////////////////////////////////////////////////////////////////

            //This is code to write the solutions to the moeaOutputLocation file. 
            //Read in CalibrationCSVFile
            //BLB

            //Create a new CalibratorConfiguration
            CalibratorConfiguration calConfig = new CalibratorConfiguration();
            try {
                calConfig.loadFromCsvFile(startupFullName);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (CalibratorConfigurationLoadFailedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }


            //Need to get information about objectives from calconfig file. 
            //Basically we need to get objective names to match with objective values. 
            String[] objectiveStrings = new String[VelmaCalibWithConstraint.getNumObjs(startupFullName)];
            List<ICalibratorConfigurationRecord> objRecs = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBJECTIVE_FUNCTION);

            for (int i = 0; i < objRecs.size(); ++i) {
                CalibratorObjectiveFunction objRecord = (CalibratorObjectiveFunction)objRecs.get(i);
                objectiveStrings[i] = objRecord.getObjectiveChoice() + '=' + objRecord.getObjectiveKeyName();
            }

            //call function to get 3 hashMaps -- only use names to print names=
            TreeMap<String, String> paramsMap1 = runMOEA_nsgaiii.getParamsMap(calConfig, currPop.get(0));
            TreeMap<String, String> followersMap1 = runMOEA_nsgaiii.getFollowersMap(calConfig, currPop.get(0));
            TreeMap<String, String> exclusivesMap1 = runMOEA_nsgaiii.getExclusivesMap(calConfig, currPop.get(0));

            //Add headers to moeaOutput.csv file. 
            List<ICalibratorConfigurationRecord> moeaOutputLocation = calConfig.getRecords(CalibratorConfiguration.RecordId.MOEA_OUTPUT_LOCATION);
            CalibratorMoeaOutputLocation outputLoc = (CalibratorMoeaOutputLocation)moeaOutputLocation.get(0);         
            String filename4 = outputLoc.getFullLocationName();

            FileWriter fw;
            try {
                fw = new FileWriter(filename4,true);

                fw.write("objectives,");
                for (int i = 0; i < VelmaCalibWithConstraint.getNumObjs(startupFullName); i++) {
                    fw.write(objectiveStrings[i] + ",");
                }
                fw.write("variables,");
                //loop through 3 hashmaps and write column names out. 
                for (Map.Entry<String,String> keys : paramsMap1.entrySet()) {
                    String keyName = keys.getKey();
                    fw.write(keyName + ",");
                }
                fw.write("followers,");
                for (Map.Entry<String,String> keys : followersMap1.entrySet()) {
                    String keyName = keys.getKey();
                    fw.write(keyName + ",");
                }
                fw.write("exclusives,");
                for (Map.Entry<String,String> keys : exclusivesMap1.entrySet()) {
                    String keyName = keys.getKey();
                    fw.write(keyName + ",");                            
                }
                fw.write("timestamp");
                fw.write(String.format("%n"));


                //now loop through solutions to get 3 hashMaps for each solution. 
                //and print outputs. 
                for (int s = 0; s < currPop.size(); s++) {

                    //Read a solution. 
                    Solution indSoln = currPop.get(s);
                    TreeMap<String, String> paramsMap = runMOEA_nsgaiii.getParamsMap(calConfig, indSoln);
                    TreeMap<String, String> followersMap = runMOEA_nsgaiii.getFollowersMap(calConfig, indSoln);
                    TreeMap<String, String> exclusivesMap = runMOEA_nsgaiii.getExclusivesMap(calConfig, indSoln);

                    fw.write("objectives,");
                    //Write all objective values
                    for (int i = 0; i < indSoln.getNumberOfObjectives(); i++) {
                        CalibratorObjectiveFunction objRecord = (CalibratorObjectiveFunction)objRecs.get(i);                      
                        double objectiveValue = indSoln.getObjective(i);
                        if (objRecord.getObjectiveChoice().equals("NSE")) {
                            objectiveValue = -objectiveValue;
                        }  // Print NSE values "opposite"
                        fw.write(objectiveValue + ",");
                    }
                    //Write variables 
                    fw.write("variables,");
                    //Write all variable values. 
                    //loop through 3 hashmaps and write column names out. 
                    for (Map.Entry<String,String> values : paramsMap.entrySet()) {
                        String value = values.getValue();
                        fw.write(value + ",");
                    }
                    fw.write("followers,");
                    for (Map.Entry<String,String> values : followersMap.entrySet()) {
                        String value = values.getValue();
                        fw.write(value + ",");
                    }
                    fw.write("exclusives,");
                    for (Map.Entry<String,String> values : exclusivesMap.entrySet()) {
                        String value = values.getValue();
                        fw.write(value + ",");                            
                    }

                    //Print timestamp.
                    fw.write(Utils.getRealtimeStamp() + ",");
                    fw.write(String.format("%n"));//appends the string to the file

                    paramsMap.clear();
                    followersMap.clear();
                    exclusivesMap.clear();
                }

                fw.close();    
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            //this catch is for writing the moeaoutput.csv file.



        } //this is end of while loop going for XX iterations. 



        algorithm.terminate();
        executor.shutdownNow();
    }

    public static TreeMap<String, String> getParamsMap(CalibratorConfiguration calConfig, Solution indSoln) { 
        //Get kvPropertyOverrides from params, exclusives, and followers
        TreeMap<String, String> paramsMap = new TreeMap<String, String>(); 
        TreeMap<String, String> followersMap = new TreeMap<String, String>(); 
        TreeMap<String, String> exclusivesMap = new TreeMap<String, String>(); 

        List<ICalibratorConfigurationRecord> params = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER);
        List<ICalibratorConfigurationRecord> followers = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_FOLLOW);
        List<ICalibratorConfigurationRecord> exclusives = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_EXCLUSIVE);

        //Load in VELMA Active "Parameters" 
        int count = 0; 
        //Loops through Records/parameters 
        for (int i = 0; i < params.size(); ++i) {
            CalibratorVelmaParameter velmaRecord = (CalibratorVelmaParameter)params.get(i);         

            if (velmaRecord.getDefaultValueItemCount() == 1) {
                Variable tempVar = indSoln.getVariable(count);
                Double tempDouble = Double.parseDouble(tempVar.toString());
                paramsMap.put(velmaRecord.getVelmaKeyName(), velmaRecord.getFormattedText(tempDouble));
                count = count + 1; 
            }
            else {
                String temp = velmaRecord.getDefaultValue();
                List<String> tempList = Arrays.asList(temp.split(","));
                String[] tempList2 = new String[tempList.size()];
                tempList2 = tempList.toArray(tempList2);

                for (int ivary : velmaRecord.getVaryingItemIndices()){
                    Variable tempVar = indSoln.getVariable(count);
                    Double tempDouble = Double.parseDouble(tempVar.toString());
                    tempList2[ivary] = velmaRecord.getFormattedText(tempDouble);
                    count = count + 1; 
                }

                //Trying to convert String array to Single String. 
                StringBuilder nameBuilder = new StringBuilder();
                for (String n : tempList2) {
                    nameBuilder.append(n).append(",");
                }
                nameBuilder.deleteCharAt(nameBuilder.length() - 1);
                String tempOut = nameBuilder.toString();
                //End of Trying to Convert Sting Array to Single String.

                paramsMap.put(velmaRecord.getVelmaKeyName(),tempOut);

            }
        }

        //Load in VELMA Active "Followers" 
        //Loops through Records/parameters 
        for (int i = 0; i < followers.size(); ++i) {
            CalibratorVelmaParameterFollow followerRecord = (CalibratorVelmaParameterFollow)followers.get(i);           
            followersMap.put(followerRecord.getFollowerVelmaKeyName(), 
                    followersMap.get(followerRecord.getLeaderVelmaKeyName()));
        }

        //Load in VELMA Active "Exclusives" 
        //Loops through Records/parameters 
        for (int i = 0; i < exclusives.size(); ++i) {
            CalibratorVelmaExclusive exclusiveRecord = (CalibratorVelmaExclusive)exclusives.get(i);         
            exclusivesMap.put(exclusiveRecord.getVelmaKeyName(), exclusiveRecord.getDefaultValue());
        }

        return paramsMap;
    }

    public static TreeMap<String, String> getFollowersMap(CalibratorConfiguration calConfig, Solution indSoln) { 
        //Get kvPropertyOverrides from params, exclusives, and followers
        TreeMap<String, String> paramsMap = new TreeMap<String, String>(); 
        TreeMap<String, String> followersMap = new TreeMap<String, String>(); 
        TreeMap<String, String> exclusivesMap = new TreeMap<String, String>(); 

        List<ICalibratorConfigurationRecord> params = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER);
        List<ICalibratorConfigurationRecord> followers = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_FOLLOW);
        List<ICalibratorConfigurationRecord> exclusives = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_EXCLUSIVE);

        //Load in VELMA Active "Parameters" 
        int count = 0; 
        //Loops through Records/parameters 
        for (int i = 0; i < params.size(); ++i) {
            CalibratorVelmaParameter velmaRecord = (CalibratorVelmaParameter)params.get(i);         

            if (velmaRecord.getDefaultValueItemCount() == 1) {
                Variable tempVar = indSoln.getVariable(count);
                Double tempDouble = Double.parseDouble(tempVar.toString());
                paramsMap.put(velmaRecord.getVelmaKeyName(), velmaRecord.getFormattedText(tempDouble));
                count = count + 1; 
            }
            else {
                String temp = velmaRecord.getDefaultValue();
                List<String> tempList = Arrays.asList(temp.split(","));
                String[] tempList2 = new String[tempList.size()];
                tempList2 = tempList.toArray(tempList2);

                for (int ivary : velmaRecord.getVaryingItemIndices()){
                    Variable tempVar = indSoln.getVariable(count);
                    Double tempDouble = Double.parseDouble(tempVar.toString());
                    tempList2[ivary] = velmaRecord.getFormattedText(tempDouble);
                    count = count + 1; 
                }

                //Trying to convert String array to Single String. 
                StringBuilder nameBuilder = new StringBuilder();
                for (String n : tempList2) {
                    nameBuilder.append(n).append(",");
                }
                nameBuilder.deleteCharAt(nameBuilder.length() - 1);
                String tempOut = nameBuilder.toString();
                //End of Trying to Convert Sting Array to Single String.

                paramsMap.put(velmaRecord.getVelmaKeyName(),tempOut);

            }
        }

        //Load in VELMA Active "Followers" 
        //Loops through Records/parameters 
        for (int i = 0; i < followers.size(); ++i) {
            CalibratorVelmaParameterFollow followerRecord = (CalibratorVelmaParameterFollow)followers.get(i);           
            followersMap.put(followerRecord.getFollowerVelmaKeyName(), 
                    followersMap.get(followerRecord.getLeaderVelmaKeyName()));
        }

        //Load in VELMA Active "Exclusives" 
        //Loops through Records/parameters 
        for (int i = 0; i < exclusives.size(); ++i) {
            CalibratorVelmaExclusive exclusiveRecord = (CalibratorVelmaExclusive)exclusives.get(i);         
            exclusivesMap.put(exclusiveRecord.getVelmaKeyName(), exclusiveRecord.getDefaultValue());
        }

        return followersMap;
    }
    public static TreeMap<String, String> getExclusivesMap(CalibratorConfiguration calConfig, Solution indSoln) { 
        //Get kvPropertyOverrides from params, exclusives, and followers
        TreeMap<String, String> paramsMap = new TreeMap<String, String>(); 
        TreeMap<String, String> followersMap = new TreeMap<String, String>(); 
        TreeMap<String, String> exclusivesMap = new TreeMap<String, String>(); 

        List<ICalibratorConfigurationRecord> params = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER);
        List<ICalibratorConfigurationRecord> followers = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_FOLLOW);
        List<ICalibratorConfigurationRecord> exclusives = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_EXCLUSIVE);

        //Load in VELMA Active "Parameters" 
        int count = 0; 
        //Loops through Records/parameters 
        for (int i = 0; i < params.size(); ++i) {
            CalibratorVelmaParameter velmaRecord = (CalibratorVelmaParameter)params.get(i);         

            if (velmaRecord.getDefaultValueItemCount() == 1) {
                Variable tempVar = indSoln.getVariable(count);
                Double tempDouble = Double.parseDouble(tempVar.toString());
                paramsMap.put(velmaRecord.getVelmaKeyName(), velmaRecord.getFormattedText(tempDouble));
                count = count + 1; 
            }
            else {
                String temp = velmaRecord.getDefaultValue();
                List<String> tempList = Arrays.asList(temp.split(","));
                String[] tempList2 = new String[tempList.size()];
                tempList2 = tempList.toArray(tempList2);

                for (int ivary : velmaRecord.getVaryingItemIndices()){
                    Variable tempVar = indSoln.getVariable(count);
                    Double tempDouble = Double.parseDouble(tempVar.toString());
                    tempList2[ivary] = velmaRecord.getFormattedText(tempDouble);
                    count = count + 1; 
                }

                //Trying to convert String array to Single String. 
                StringBuilder nameBuilder = new StringBuilder();
                for (String n : tempList2) {
                    nameBuilder.append(n).append(",");
                }
                nameBuilder.deleteCharAt(nameBuilder.length() - 1);
                String tempOut = nameBuilder.toString();
                //End of Trying to Convert Sting Array to Single String.

                paramsMap.put(velmaRecord.getVelmaKeyName(),tempOut);

            }
        }

        //Load in VELMA Active "Followers" 
        //Loops through Records/parameters 
        for (int i = 0; i < followers.size(); ++i) {
            CalibratorVelmaParameterFollow followerRecord = (CalibratorVelmaParameterFollow)followers.get(i);           
            followersMap.put(followerRecord.getFollowerVelmaKeyName(), 
                    followersMap.get(followerRecord.getLeaderVelmaKeyName()));
        }

        //Load in VELMA Active "Exclusives" 
        //Loops through Records/parameters 
        for (int i = 0; i < exclusives.size(); ++i) {
            CalibratorVelmaExclusive exclusiveRecord = (CalibratorVelmaExclusive)exclusives.get(i);         
            exclusivesMap.put(exclusiveRecord.getVelmaKeyName(), exclusiveRecord.getDefaultValue());
        }

        return exclusivesMap;
    }
}


