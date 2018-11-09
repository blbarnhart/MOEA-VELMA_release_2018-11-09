package org.moeaframework.examples.ga.Velma;

import gov.epa.velmamoeaapi.CalibratorConfiguration;
import gov.epa.velmamoeaapi.CalibratorConfigurationLoadFailedException;
import gov.epa.velmamoeaapi.CalibratorObjectiveFunction;
import gov.epa.velmamoeaapi.CalibratorObjectiveGroup;
import gov.epa.velmamoeaapi.CalibratorObservedSource;
import gov.epa.velmamoeaapi.CalibratorVelmaExclusive;
import gov.epa.velmamoeaapi.CalibratorVelmaParameter;
import gov.epa.velmamoeaapi.CalibratorVelmaParameterFollow;
import gov.epa.velmamoeaapi.CalibratorVelmaParameterGauss;
import gov.epa.velmamoeaapi.CalibratorVelmaSource;
import gov.epa.velmamoeaapi.ICalibratorConfigurationRecord;
import gov.epa.velmasimulator.SimulatorClock;
import gov.epa.velmamoeaapi.VelmaCalibratorCmdLine;
import gov.epa.velmamoeaapi.VelmaCalibratorResults;
import gov.epa.velmautils.ObservedDataSourceManager;
import gov.epa.velmautils.Utils;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.EncodingUtils;
import org.moeaframework.problem.AbstractProblem;


public class VelmaCalibGauss extends AbstractProblem {

    /////////////////////////////////////////////
    //MOEA problem called VelmaCalib. 
    /////////////////////////////////////////////
    //VELMA PROJECT TEAM AND 
    //B.L. Barnhart, U.S. EPA, 2015-11-19
    //
    /////////////////////////////////////////////

    protected String configFileLocation;

    public VelmaCalibGauss(String configFileLocation) {
        super(getNumVars(configFileLocation),
                getNumObjs(configFileLocation));
        //                getNumConstraints());
        this.configFileLocation = configFileLocation;

    }

    //Solution describes the entire population. It sets variables to 
    //be within limits (mins, maxes) specified by the CalibrationConfiguration CSV FILE.
    @Override
    public Solution newSolution() {

        Solution solution = new Solution(getNumberOfVariables(), 
                getNumberOfObjectives());
        //                , getNumberOfConstraints());

        double[] minsMaxesDouble = getMinsMaxes();

        //BLB believes the below logic makes sense... 2016-01-20
        //Each VELMA variable consists of 4 gaussian variables,
        //i.e., meanMin, meanMax, sdevMin, sdevMax
        int i = 0;
        int j = 0; 
        int testParam = minsMaxesDouble.length/4;
        int testParamCounter = 0;
        while (testParamCounter < testParam) {
            solution.setVariable(i, EncodingUtils.newReal(minsMaxesDouble[j], minsMaxesDouble[(j+1)]));
            solution.setVariable(i+1, EncodingUtils.newReal(minsMaxesDouble[j+2], minsMaxesDouble[(j+3)]));
            i = i + 2;
            j = j + 4; 
            testParamCounter = testParamCounter + 1;
        }

        return solution;
    }

    //The evaluate method runs VELMA and calculates the specified objectives
    //according to the input CalibrationConfiguration CSV FILE. 
    //It is important to only put output (i.e., print to screen) in the 
    //runMOEA_nsgaii.java file to avoid problems with parallel computation. 
    @Override
    public void evaluate(Solution solution) {
        double[] finalObjectives = new double[numberOfObjectives];
        double[] f = new double[numberOfObjectives];
        //        double[] g = new double[numberOfConstraints];

        double[] x = new double[numberOfVariables];
        String[] objectiveStrings = new String[numberOfObjectives];
        for (int i = 0; i < x.length; ++i) {
            x[i] = EncodingUtils.getReal(solution.getVariable(i));
        }

        //Hard-coded Constraint Portion BLB 2015-09-23
        //g=0.0 means that the constraint is satisfied.
        //g != 0.0 means that it is not. 
        //        if (x[1] > x[0]) {
        //            g[0] = 0.0;
        //        } else {
        //            g[0] = 99.0;
        //        }
        //        solution.setConstraint(0, g[0]);


        //************************************************************
        //Setup VELMA
        //************************************************************
        //Insert Gaussian formulation here. 
        Random randomDraw = new java.util.Random();
        int randomCounter = 0;
        int numberOfGaussianIterations = getNumberOfGaussianIterations(); 
        double[][] finalObjectivesFromRandom = new double[numberOfGaussianIterations][numberOfObjectives];
        while (randomCounter < numberOfGaussianIterations) {

            //Read in CalibrationCSVFile
            String startupFullName = getCalibrationCSVFile();

            //Create a new CalibratorConfiguration
            CalibratorConfiguration calConfig = new CalibratorConfiguration();
            try {
                calConfig.loadFromCsvFile(startupFullName);
            } catch (IOException | CalibratorConfigurationLoadFailedException e) {
                System.out.println("FAIL: cannot load data to CalibratorConfiguration from file \"" + startupFullName + "\"");
                e.printStackTrace();
            }

            //Get the name of the XML File 
            String simConfigXmlFullName = null; 
            for (ICalibratorConfigurationRecord icr : calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_SOURCE)) {
                String fullName = ((CalibratorVelmaSource)icr).getFullFileName();
                if (Utils.isNullOrEmpty(fullName) == false) {
                    simConfigXmlFullName = fullName;
                    break;
                }
            }
            if (simConfigXmlFullName == null) {
                System.out.println("FAIL: CalibratorConfiguration file + \"" + startupFullName + "\" doesn't specify a VELMA_SOURCE record.");
            }

            //Get kvPropertyOverrides from params, exclusives, and followers
            Map<String, String> kvPropertyOverrides = new HashMap<String, String>(); 
            List<ICalibratorConfigurationRecord> params = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_GAUSS);
            List<ICalibratorConfigurationRecord> exclusives = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_EXCLUSIVE);
            List<ICalibratorConfigurationRecord> followers = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_FOLLOW);

            //Load in VELMA Active "Parameters" 
            int count = 0; 
            //Loops through Records/parameters 
            for (int i = 0; i < params.size(); ++i) {
                CalibratorVelmaParameterGauss velmaRecord = (CalibratorVelmaParameterGauss)params.get(i);         

                if (velmaRecord.getDefaultValueItemCount() == 1) {
                    kvPropertyOverrides.put(velmaRecord.getVelmaKeyName(), 
                            velmaRecord.getFormattedText(
                                    (randomDraw.nextGaussian() * x[count+1]) + x[count] //equiv to G*sdev + mean
                                    )
                            );
                    count = count + 2; 
                }
                else {
                    String temp = velmaRecord.getDefaultValue();
                    List<String> tempList = Arrays.asList(temp.split(","));
                    String[] tempList2 = new String[tempList.size()];
                    tempList2 = tempList.toArray(tempList2);

                    for (int ivary : velmaRecord.getVaryingItemIndices()){
                        tempList2[ivary] = velmaRecord.getFormattedText(
                                (randomDraw.nextGaussian() * x[count+1]) + x[count] // equiv to G*sdev + mean for each ivary
                                );
                        count = count + 2; 
                    }

                    //Trying to convert String array to Single String. 
                    StringBuilder nameBuilder = new StringBuilder();
                    for (String n : tempList2) {
                        nameBuilder.append(n).append(",");
                    }
                    nameBuilder.deleteCharAt(nameBuilder.length() - 1);
                    String tempOut = nameBuilder.toString();
                    //End of Trying to Convert Sting Array to Single String.

                    kvPropertyOverrides.put(velmaRecord.getVelmaKeyName(),tempOut);

                }
            }

            //Load in VELMA Active "Followers" 
            //Loops through Records/parameters 
            for (int i = 0; i < followers.size(); ++i) {
                CalibratorVelmaParameterFollow followerRecord = (CalibratorVelmaParameterFollow)followers.get(i);           
                kvPropertyOverrides.put(followerRecord.getFollowerVelmaKeyName(), 
                        kvPropertyOverrides.get(followerRecord.getLeaderVelmaKeyName()));
            }

            //Load in VELMA Active "Exclusives" 
            //Loops through Records/parameters 
            for (int i = 0; i < exclusives.size(); ++i) {
                CalibratorVelmaExclusive exclusiveRecord = (CalibratorVelmaExclusive)exclusives.get(i);         
                kvPropertyOverrides.put(exclusiveRecord.getVelmaKeyName(), exclusiveRecord.getDefaultValue());
            }

            //Get Observed Sources List 
            List<ICalibratorConfigurationRecord> obsRecs = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBSERVED_SOURCE);
            ObservedDataSourceManager obsManager = new ObservedDataSourceManager();
            //Loops through Observed Records and Loads up the obsManager
            for (int i = 0; i < obsRecs.size(); ++i) {
                CalibratorObservedSource obsRecord = (CalibratorObservedSource)obsRecs.get(i);          
                obsManager.loadFromCsvFile(obsRecord.getObservedKeyName(), obsRecord.getFullFileName(), true);
            }

            //Get Observed KeyNames for Objectives List
            List<ICalibratorConfigurationRecord> objRecs = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBJECTIVE_FUNCTION);
            Set<String> objObsKeys = new HashSet<String>();
            for (int i = 0; i < objRecs.size(); ++i) {
                CalibratorObjectiveFunction objRecord = (CalibratorObjectiveFunction)objRecs.get(i);            
                objObsKeys.add(objRecord.getObservedKeyName());
            }

            //Get Unique Objective Keys
            Set<String> objKeys = new HashSet<String>();
            for (int i = 0; i < objRecs.size(); ++i) {
                CalibratorObjectiveFunction objRecord = (CalibratorObjectiveFunction)objRecs.get(i);            
                objKeys.add(objRecord.getObjectiveKeyName());
            }

            //Set objectivesToInclude
            TreeMap<String, Double> allObjectivesToInclude = new TreeMap<String, Double>();

            //Get Results List 
            List<ICalibratorConfigurationRecord> resultsRecords = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_RESULT);


            //          ************************************************************
            //          RUN VELMA
            //          ************************************************************
            //        String xmlIDName = IoUtils.getFileNameWithoutExtension(filenameOutput) + "_MOEA_ID_" + ".xml";  

            VelmaCalibratorResults vcr = VelmaCalibratorCmdLine.runVelmaForCalibrationResults(simConfigXmlFullName, kvPropertyOverrides, resultsRecords);

            int numOfLoops = vcr.getNumberOfLoops();

            // If VELMA crashes, then set all objectives to 99999.999; 
            if (vcr.isAllLoopsDone() != true) {
                //      if (vcr.getLastCompletedStep() != (vcr.getTotalNumberOfSteps()*numOfLoops)) {
                System.out.println("ERROR: Obs and Sim do not have lengths that are exact multiples!");
                System.out.println("Last Completed Step is: " + vcr.getLastCompletedStep() + 
                        " and Total Number Of Steps are : " + vcr.getTotalNumberOfSteps() +
                        "and numberOfLoops is/are: " + numOfLoops);


                //Setting objectives to biggest value possible to throw these soln's out. 
                for (int i = 0; i < numberOfObjectives; i++) { finalObjectives[i] = Double.MAX_VALUE; }


            }
            else {
                System.out.println("SUCCESS: Obs and Sim have lengths that are exact multiples!");
                System.out.println("Last Completed Step is: " + vcr.getLastCompletedStep() + 
                        " and Total Number Of Steps are : " + vcr.getTotalNumberOfSteps() +
                        "and numberOfLoops is/are: " + numOfLoops);


                //          ************************************************************
                //          Calculate Objectives Now. 
                //          ************************************************************
                //            Map<String, Double> objectivesToInclude = new HashMap<String, Double>(); 

                for (int i = 0; i < objRecs.size(); ++i) {
                    CalibratorObjectiveFunction objRecord = (CalibratorObjectiveFunction)objRecs.get(i);            

                    allObjectivesToInclude.put(objRecord.getObjectiveKeyName(), 
                            getObjectiveCalculation(vcr, 
                                    objRecord.getObservedKeyName(),
                                    objRecord.getObjectiveChoice(),
                                    objRecord.getStartYear(),
                                    objRecord.getEndYear(),
                                    objRecord.getBaseThreshold(),
                                    objRecord.getPeakThreshold())
                            ); 


                    //need to change soon. TODO
                    //                objectiveStrings[i] = objRecord.getObjectiveChoice() + '=' + objRecord.getObjectiveKeyName();
                }

                //get objective groups
                List<ICalibratorConfigurationRecord> objGroupRecs = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBJECTIVE_GROUP);
                if (objGroupRecs.size() > 0) {

                    for (int i = 0; i < objGroupRecs.size(); ++i) {
                        CalibratorObjectiveGroup objGroupRecord = (CalibratorObjectiveGroup)objGroupRecs.get(i);            
                        List<String> indivKeyNameCollection = objGroupRecord.getObjectiveKeynameCollection();

                        for (String key : allObjectivesToInclude.keySet()) {
                            if(indivKeyNameCollection.contains(key)) {
                                finalObjectives[i] = finalObjectives[i] + allObjectivesToInclude.get(key);
                            }
                        }
                    }
                }
                else {
                    int tempCounter = 0;
                    for (String key : allObjectivesToInclude.keySet()) {
                        finalObjectives[tempCounter] = allObjectivesToInclude.get(key);
                        tempCounter = tempCounter + 1; 
                    }
                }

            }        

            // Print actual objectives to screen. 
            for (String key : allObjectivesToInclude.keySet()) {
                System.out.println("Min or -Max Objectives: ");
                System.out.println(key + "," + allObjectivesToInclude.get(key));
            }


            //TODO Need to add code to include account for means and stdevs of objectives
            //Somehow Save double array into something larger so that you can save 10 of them. 
            for (int i = 0; i < finalObjectives.length; i++) {
                finalObjectivesFromRandom[randomCounter][i] = finalObjectives[i];
            }
            randomCounter = randomCounter + 1;
            System.out.println("Beginning RandomTrial Simulator # " + 
                    (randomCounter+1) + " of " + numberOfGaussianIterations);
        } // end of Random For Loop
        //Now get average and stdev of double array. 


        //Set objectives to be means of all randomObjectives 
        //TODO could add standard deviation of all randomObjectives as another objective. 
        for (int i = 0; i < numberOfObjectives; i++) {
            double[] objectiveArray = new double[numberOfGaussianIterations];
            for (int j = 0; j < numberOfGaussianIterations; j++) {
                objectiveArray[j] = finalObjectivesFromRandom[j][i];
            }
            double[] objectiveMeanStds = getArrayMeanAndStd(objectiveArray);
            if (Double.isInfinite(objectiveMeanStds[0])) { objectiveMeanStds[0] = Double.MAX_VALUE; }
            solution.setObjective(i, objectiveMeanStds[0]);
        }

        // Set objectives to values from getObjectiveCalculation.
        //        for (int i = 0; i < finalObjectives.length; i++) {
        //            solution.setObjective(i, finalObjectives[i]);
        //        }        
        //end of evaluation method.
    }


    /* Obtains the number of objective functions or function groups
     * specified in the Calibration Configuration CSV FILE. 
     */
    public static int getNumObjs(String configFileLocation) { 

        //initialize number of objectives to 0.
        int numOfObjectivesOutput = 0; 

        // Load in CalConfig CSV File. 
        CalibratorConfiguration calConfig = new CalibratorConfiguration();
        try {
            try {
                calConfig.loadFromCsvFile(configFileLocation);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (CalibratorConfigurationLoadFailedException e) {
            e.printStackTrace();
        }

        //Make a hashSet of ObjectiveGroupKeyNames
        List<ICalibratorConfigurationRecord> allObjectiveGroups = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBJECTIVE_GROUP);
        Set<String> totalObjGroupKeyNames = new HashSet<String>();

        for (int i = 0; i < allObjectiveGroups.size(); ++i) {
            CalibratorObjectiveGroup singleGroup = (CalibratorObjectiveGroup)allObjectiveGroups.get(i);
            totalObjGroupKeyNames.add(singleGroup.getGroupKeyName());
        }
        //if groups are present, set the objective number output to the number of groups. 
        numOfObjectivesOutput = totalObjGroupKeyNames.size();


        //If no groups are present in the CalConfig file, then count the individual objectives. 
        if (numOfObjectivesOutput < 1) {

            //Make a hashSet of ObjectiveKeyNames
            List<ICalibratorConfigurationRecord> allObjectives = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBJECTIVE_FUNCTION);
            Set<String> totalObjectiveKeyNames = new HashSet<String>();

            for (int i = 0; i < allObjectives.size(); ++i) {
                CalibratorObjectiveFunction singleObjective = (CalibratorObjectiveFunction)allObjectives.get(i);            
                totalObjectiveKeyNames.add(singleObjective.getObjectiveKeyName()); 
            }
            //if no groups present, set objective number output to be the number of individual objectives
            numOfObjectivesOutput = totalObjectiveKeyNames.size();
        }

        System.out.println("");
        System.out.println("The number of objectives used in this run is/are: " + numOfObjectivesOutput);
        System.out.println("");

        return numOfObjectivesOutput;
    }

    public static int getNumVars(String configFileLocation) { 
        // Get the total number of variables from the Calibrator Configuration File. 
        //      Note that this only includes VELMA_PARAMETERS and does not include 
        //      either VELMA_EXCLUSIVES or VELMA_FOLLOWERS
        //      BLB 2015-09-14

        // Read in calibrator Configuration file. 
        CalibratorConfiguration calConfig = new CalibratorConfiguration();
        try {
            try {
                calConfig.loadFromCsvFile(configFileLocation);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (CalibratorConfigurationLoadFailedException e) {
            e.printStackTrace();
        }

        List<ICalibratorConfigurationRecord> params = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_GAUSS);

        List<String> varsNumber = new ArrayList<String>();

        for (int i = 0; i < params.size(); ++i) {
            CalibratorVelmaParameterGauss velmaRecord = (CalibratorVelmaParameterGauss)params.get(i);

            for (int ivary : velmaRecord.getVaryingItemIndices()) {
                varsNumber.add(String.valueOf(velmaRecord.getVaryingMeanMin(ivary)));
                varsNumber.add(String.valueOf(velmaRecord.getVaryingSdevMin(ivary)));

            }
        }

        System.out.println("Number of GA-varying variables = " + varsNumber.size());
        System.out.println("ERRORS OCCUR WHEN THIS EQUALS 0 // CHECK CSV LOCATION " + varsNumber.size());

        return varsNumber.size();
    }

    //    public static int getNumConstraints() { 
    //        return 1; 
    //    }

    public double[] getMinsMaxes() { 
        // This gets the mins and maxes from the VELMA_PARAMETERS_GAUSS from the Calibrator
        //      Configuration CSV file. 

        // Read in calConfig CSV fil.e 
        CalibratorConfiguration calConfig = new CalibratorConfiguration();
        try {
            try {
                String startupFullName = getCalibrationCSVFile();
                calConfig.loadFromCsvFile(startupFullName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (CalibratorConfigurationLoadFailedException e) {
            e.printStackTrace();
        }

        // Get Parameters.
        List<ICalibratorConfigurationRecord> params = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.VELMA_PARAMETER_GAUSS);

        List<String> paramsMinMax = new ArrayList<String>();

        //Loops through Records/parameters and picks out max and mins. 
        for (int i = 0; i < params.size(); ++i) {
            CalibratorVelmaParameterGauss velmaRecord = (CalibratorVelmaParameterGauss)params.get(i);

            //get maxs and mins from parameters used to send to GA. 
            for (int ivary : velmaRecord.getVaryingItemIndices()) {
                paramsMinMax.add(String.valueOf(velmaRecord.getVaryingMeanMin(ivary)));
                paramsMinMax.add(String.valueOf(velmaRecord.getVaryingMeanMax(ivary)));
                paramsMinMax.add(String.valueOf(velmaRecord.getVaryingSdevMin(ivary)));
                paramsMinMax.add(String.valueOf(velmaRecord.getVaryingSdevMax(ivary)));
            }
        }

        //Convert from List<String> to String[]
        String[] minsMaxes = new String[paramsMinMax.size()];
        //      String[] typesUse = new String[paramsTypes.size()];
        minsMaxes = paramsMinMax.toArray(minsMaxes);
        //      typesUse = paramsTypes.toArray(typesUse);

        //Convert from String[] to double[]
        double[] minsMaxesDouble = new double[minsMaxes.length];
        for (int i = 0; i < minsMaxesDouble.length; ++i) {
            minsMaxesDouble[i] = Double.parseDouble(minsMaxes[i]);
        }

        return minsMaxesDouble; 
    }

    public double getObjectiveCalculation(
            VelmaCalibratorResults vcr,
            String obsKeyName, 
            String objChoice,
            int sYear,
            int eYear,
            double baseThresh,
            double peakThresh) {

        double objectiveOutput = Double.MAX_VALUE;
        //      int numOfLoops = vcr.getNumberOfLoops();
        //      int loopCount = 1; //manual way of tracking loops.  

        //Read in CalibrationCSVFile
        String startupFullName = getCalibrationCSVFile();

        //Create a new CalibratorConfiguration
        CalibratorConfiguration calConfig = new CalibratorConfiguration();
        try {
            calConfig.loadFromCsvFile(startupFullName);
        } catch (IOException | CalibratorConfigurationLoadFailedException e) {
            System.out.println("FAIL: cannot load data to CalibratorConfiguration from file \"" + startupFullName + "\"");
            e.printStackTrace();
        }

        //Get Observed Sources List 
        List<ICalibratorConfigurationRecord> obsRecs = calConfig.getActiveRecords(CalibratorConfiguration.RecordId.OBSERVED_SOURCE);
        ObservedDataSourceManager obsManager = new ObservedDataSourceManager();
        //Loops through Observed Records and Loads up the obsManager
        for (int i = 0; i < obsRecs.size(); ++i) {
            CalibratorObservedSource obsRecord = (CalibratorObservedSource)obsRecs.get(i);          
            obsManager.loadFromCsvFile(obsRecord.getObservedKeyName(), obsRecord.getFullFileName(), true);
        }

        if (obsManager.hasDataSource(obsKeyName)==true) {

            SimulatorClock resultsClock = new SimulatorClock(sYear,eYear); 
            List<Double> obsValuesList = new ArrayList<Double>();
            List<Double> simValuesList = new ArrayList<Double>();

            //Note that this clock only has one loop (the last one). 
            while ( resultsClock.isAllLoopsDone() == false ) {

                int year = resultsClock.getYear();
                int jday = resultsClock.getDayOfYear();

                if (obsManager.hasDataValue(obsKeyName, year, jday) == true) {
                    if (vcr.hasDataValue(obsKeyName, year, jday) == true) {

                        double obsValue = obsManager.getDataValue(obsKeyName, year, jday);
                        double vcrValue = vcr.getDataValue(obsKeyName, year, jday);

                        //insert here base and peak threshold limits
                        //This looks only at values above or below threshold limits
                        //specified by the user. 2015-08-26 BLB
                        if (obsValue <= baseThresh) { 
                            obsValuesList.add(obsValue);
                            simValuesList.add(vcrValue);
                        }
                        else if (obsValue > peakThresh) {
                            obsValuesList.add(obsValue);
                            simValuesList.add(vcrValue);
                        }
                        else if (Double.isNaN(baseThresh) == true && Double.isNaN(peakThresh) == true) {
                            obsValuesList.add(obsValue);
                            simValuesList.add(vcrValue);
                        }
                    }
                }

                resultsClock.incrementDay(); 
                // This is needed if using a clock with more than one loop. 
                // i.e., not this case. 
                //              if (resultsClock.isAtLoopStart()) {
                //                  resultsClock.resetCalendar(); //restart calendar at end of loop.
                //              }
            }

            //MOEA only minimizes functions, so anything that needs to be 
            // maximized needs to be negated here. 
            if (objChoice.equals("NSE")) { 
                objectiveOutput = -getListNSE(obsValuesList,simValuesList);
                System.out.println();
            }
            else if (objChoice.equals("RMSE")) { 
                objectiveOutput = getListRMSE(obsValuesList,simValuesList);
            }
            else if (objChoice.equals("MSRE")) {
                objectiveOutput = getListMSRE(obsValuesList,simValuesList);
            }
            else if (objChoice.equals("TRMSE")) { 
                objectiveOutput = getListTRMSE(obsValuesList,simValuesList);
            }
            else {
                System.out.println("FAIL: Objective Choice not allowable: Must be NSE, RMSE, MSRE, or TRMSE");
                System.out.println("FAIL: Objective Choice not allowable: Must be NSE, RMSE, MSRE, or TRMSE");
                System.out.println("FAIL: Objective Choice not allowable: Must be NSE, RMSE, MSRE, or TRMSE");
                objectiveOutput = Double.MAX_VALUE;
            }
            return objectiveOutput;

        }

        else {
            System.out.println("FAIL: Do not have data source specified for " + obsKeyName);
            System.out.println("FAIL: SETTING OBJECTIVE VALUE TO maximum Value = SHOULD HALT RUN!");
            return Double.MAX_VALUE;
        }

    }


    public static double getListNSE(List<Double> obsData, List<Double> simData) {
        // Calculates the Nash-Sutcliffe (NS, 1970) value using a single mean. 

        double[] obsDataD = new double[obsData.size()];
        for (int j = 0; j < obsDataD.length; j++) {
            obsDataD[j] = obsData.get(j);         
        }

        double[] simDataD = new double[simData.size()];
        for (int j = 0; j < simDataD.length; j++) {
            simDataD[j] = simData.get(j);         
        }

        double[] numNSE1 = new double[obsDataD.length];
        double[] denomNSE1 = new double[obsDataD.length];
        double meanObs = sum(obsDataD)/obsDataD.length;

        //Note that this loops through the observed Data. 
        double numNSE = 0.0;
        double denomNSE = 0.0;


        for (int i = 0; i < (obsDataD.length); i++) {
            numNSE1[i] = Math.pow((simDataD[i] - obsDataD[i]),2);
            numNSE += numNSE1[i];
        }   
        for (int i = 0; i < (obsDataD.length); i++) {
            denomNSE1[i] = Math.pow((obsDataD[i] - meanObs),2);
            denomNSE += denomNSE1[i];
        }   

        double temp22 = (1 - (numNSE/denomNSE));

        //      System.out.print("numNSE=" + numNSE);
        //      System.out.print(" denomNSE=" + denomNSE);
        //      System.out.print(" temp22=" + temp22);
        //      System.out.println();
        //      return Double.parseDouble(df2.format(temp22));
        return temp22;
    }

    public static double getListTRMSE(List<Double> obsData, List<Double> simData) {

        double[] obsDataD = new double[obsData.size()];
        for (int j = 0; j < obsDataD.length; j++) {
            obsDataD[j] = obsData.get(j);         
        }

        double[] simDataD = new double[simData.size()];
        for (int j = 0; j < simDataD.length; j++) {
            simDataD[j] = simData.get(j);         
        }

        double[] hatQs = new double[obsDataD.length];
        double[] hatQo = new double[obsDataD.length];
        double[] diffhatsq = new double[obsDataD.length];
        double lambda = 0.3;

        for (int i = 0; i < obsDataD.length; i++) {
            hatQs[i] = (Math.pow((1+simDataD[i]), lambda) - 1) / lambda;
            hatQo[i] = (Math.pow((1+obsDataD[i]), lambda) - 1) / lambda;
            diffhatsq[i] = Math.pow((hatQs[i] - hatQo[i]),2);
        }   

        double TRMSE = Math.sqrt(sum(diffhatsq)/diffhatsq.length);

        return (TRMSE); 
    }


    public static double getListRMSE(List<Double> obsData, List<Double> simData) {


        double[] obsDataD = new double[obsData.size()];
        for (int j = 0; j < obsDataD.length; j++) {
            obsDataD[j] = obsData.get(j);         
        }

        double[] simDataD = new double[simData.size()];
        for (int j = 0; j < simDataD.length; j++) {
            simDataD[j] = simData.get(j);         
        }

        //length of obsData is 1461, length of sim data should be 2922;
        double[] diffsq = new double[obsDataD.length];

        for (int i = 0; i < obsDataD.length; i++) {
            diffsq[i] = Math.pow((obsDataD[i]-simDataD[i]),2);
        }   

        double RMSE = Math.sqrt(sum(diffsq)/(diffsq.length));
        DecimalFormat df2 = new DecimalFormat("0.000");

        return Double.parseDouble(df2.format(RMSE));
    }

    public static double getListMSRE(List<Double> obsData, List<Double> simData) {


        double[] obsDataD = new double[obsData.size()];
        for (int j = 0; j < obsDataD.length; j++) {
            obsDataD[j] = obsData.get(j);         
        }

        double[] simDataD = new double[simData.size()];
        for (int j = 0; j < simDataD.length; j++) {
            simDataD[j] = simData.get(j);         
        }

        double[] diffsq = new double[obsDataD.length];

        for (int i = 0; i < obsDataD.length; i++) {
            diffsq[i] = Math.pow(
                    ( ((obsDataD[i] + 1e-7) - (simDataD[i] + 1e-7)) / (obsDataD[i] + 1e-7) )
                    , 2);
        }   

        double RMSE = sum(diffsq)/diffsq.length;

        DecimalFormat df2 = new DecimalFormat("0.000");

        return Double.parseDouble(df2.format(RMSE));
    }

    public static double sum(double...values) {
        double result = 0;
        for (double value:values)
            result += value;
        return result;
    }

    public String getCalibrationCSVFile(){  
        //      String output = "C:\\Brad\\AProjects\\AADecRun\\KonzaSmall_30m\\Konza_Test_CalibratorConfiguration_runoff.csv";
        //      return output;
        return configFileLocation;

    }

    public static int getNumberOfGaussianIterations() {
        return 10; 
    }
    
    public static double[] getArrayMeanAndStd(double a[]) {

        double sum = 0.0; 
        double[] output = new double[2];

        int count = 0; 
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
            count++;
        }
        System.out.println("Calculating Means and Stdevs of Objectives");
        double mean = sum / count; 
        output[0] = mean;

        double[] temp = new double[a.length];
        double sum2 = 0.0;
        for (int i = 0; i < a.length; i++) {
            temp[i] = Math.pow(a[i]-mean,2);
            sum2 += temp[i];
        }
        double stdevs = Math.sqrt(sum2);
        output[1] = stdevs;

        return output; 
    }




}
