/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.clas.detector.clas12calibration.dc.plots;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.clas.detector.clas12calibration.dc.analysis.Coordinate;
import org.clas.detector.clas12calibration.viewer.AnalysisMonitor;
import org.clas.detector.clas12calibration.viewer.Viewer;
import org.freehep.math.minuit.FCNBase;
import org.freehep.math.minuit.FunctionMinimum;
import org.freehep.math.minuit.MnMigrad;
import org.freehep.math.minuit.MnScan;
import org.freehep.math.minuit.MnUserParameters;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent; 
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.timetodistance.TimeToDistanceEstimator;
import org.jlab.utils.groups.IndexedList;
import org.jlab.utils.system.ClasUtilsFile;
/**
 *
 * @author ziegler
 */
public class PlotMaker extends AnalysisMonitor{
    public HipoDataSync writer = null;
    private HipoDataEvent hipoEvent = null;
    private SchemaFactory schemaFactory = new SchemaFactory();
    PrintWriter pw = null;
    public PlotMaker(String name, ConstantsManager ccdb) throws FileNotFoundException {
        super(name, ccdb);
        this.setAnalysisTabNames("TrackDoca vs T","TrackDoca vs T Graphs","CalcDoca vs T","Time Residuals","Parameters");
        this.init(false, "v0:vmid:R:tmax:distbeta:delBf:b1:b2:b3:b4");
        pw = new PrintWriter(new File("ccdbConstants.txt"));
        pw.printf("#& sector superlayer component v0 deltanm tmax distbeta delta_bfield_coefficient b1 b2 b3 b4 delta_T0 c1 c2 c3\n");
        
        String dir = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        schemaFactory.initFromDirectory(dir);
       
        if(schemaFactory.hasSchema("TimeBasedTrkg::TBHits")) {
            System.out.println(" BANK FOUND........");
        } else {
            System.out.println(" BANK NOT FOUND........");
        }
        writer = new HipoDataSync(schemaFactory);
        writer.setCompressionType(2);
        hipoEvent = (HipoDataEvent) writer.createEvent();
        writer.open("TestOutPut.hipo");
        writer.writeEvent(hipoEvent);
        
        //init BBin Centers
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < this.alphaBins; j++) {
                for (int k = 0; k < this.BBins; k++) {
                    BfieldValuesUpd[i][j][k] = BfieldValues[k];
                }
            }
        }
    }
    private Map<Coordinate, H2F> Tvstrkdocas                = new HashMap<Coordinate, H2F>();
    private Map<Coordinate, H2F> Tvscalcdocas               = new HashMap<Coordinate, H2F>();
    private Map<Coordinate, GraphErrors> TvstrkdocasProf    = new HashMap<Coordinate, GraphErrors>();
    private Map<Coordinate, FitFunction> TvstrkdocasFit             = new HashMap<Coordinate, FitFunction>();
    private Map<Coordinate, MnUserParameters> TvstrkdocasFitPars    = new HashMap<Coordinate, MnUserParameters>();
    public  Map<Coordinate, FitLine> TvstrkdocasFits                = new HashMap<Coordinate, FitLine>();
    private Map<Coordinate, H1F> timeResi       = new HashMap<Coordinate, H1F>();
    private Map<Coordinate, H1F> timeResiNew    = new HashMap<Coordinate, H1F>();
    private Map<Coordinate, H1F> fitResi        = new HashMap<Coordinate, H1F>();
    private Map<Coordinate, H1F> B              = new HashMap<Coordinate, H1F>(); //histogram to get B values centroids
    private Map<Coordinate, H1F> ParsVsIter    = new HashMap<Coordinate, H1F>();
    int nsl = 6;

    public static double[] BfieldValues = new double[]{0.707106781,1.224744871,1.58113883,1.87082869,2.121320344,2.34520788,2.549509757,2.738612788};   
    public static double[] AlphaValues = new double[]{-26,-22,-18,-14,-10,-6,-2,2,6,10,14,18,22,26};
    double AlphaBinHalfWidth = 2;
    public static int alphaBins = AlphaValues.length;
    public static int BBins = BfieldValues.length;
    //update middle of the B bins
    public static double[][][] BfieldValuesUpd = new double[2][alphaBins][BBins];
    @Override
    public void createHistos() {
        // initialize canvas and create histograms
        this.setNumberOfEvents(0);
        DataGroup td = new DataGroup(7,2);
        DataGroup tdp = new DataGroup(14,8);
        DataGroup cd = new DataGroup(7,2);
        DataGroup tr = new DataGroup(6,1);
        DataGroup fr = new DataGroup(6,1);
        
        int ijk = 0;
        int ij = 0;
        for (int i = 0; i < nsl; i++) {
            TvstrkdocasFitPars.put(new Coordinate(i), new MnUserParameters());
            timeResi.put(new Coordinate(i), new H1F("time residual for sly " + (i+1), 100, -0.5, 0.5)); 
            timeResiNew.put(new Coordinate(i), new H1F("time residual for sly " + (i+1), 100, -0.5, 0.5)); 
            fitResi.put(new Coordinate(i), new H1F("fit residual for sly " + (i+1), 100, -0.5, 0.5));
            
            tr.addDataSet(timeResi.get(new Coordinate(i)), i);
            tr.addDataSet(timeResiNew.get(new Coordinate(i)), i);
            fr.addDataSet(fitResi.get(new Coordinate(i)), i);
            
            for (int j = 0; j < alphaBins; j++) {
                DataGroup trkdvst = new DataGroup(1,1);
                DataGroup dvst = new DataGroup(1,1);
                
                for (int k = 0; k < BBins+1; k++) {
                    DataGroup prfdvst = new DataGroup(1,1);
                    Tvstrkdocas.put(new Coordinate(i,j,k), new H2F("trkDocavsT" + (i + 1)*1000+(j+1)+26, "superlayer" + (i + 1)
                            + ", alpha ("+(AlphaValues[j]-AlphaBinHalfWidth)+", "+(AlphaValues[j]+AlphaBinHalfWidth)+")"
                            +", B "+k, 200, 0, 2.0, 200, 0, 500.0+(int)(i/2)*450.0));
                    
                    TvstrkdocasProf.put(new Coordinate(i,j,k), new GraphErrors());
                    TvstrkdocasProf.get(new Coordinate(i,j,k)).setMarkerColor(k+1);
                    
                    Tvscalcdocas.put(new Coordinate(i,j,k), new H2F("calcDocavsT" + (i + 1)*1000+(j+1)+26, "superlayer" + (i + 1)
                            + ", alpha ("+(AlphaValues[j]-AlphaBinHalfWidth)+", "+(AlphaValues[j]+AlphaBinHalfWidth)+")"
                            +", B "+k, 200, 0, 2.0, 200, 0, 500.0+(int)(i/2)*450.0));
                    tdp.addDataSet(TvstrkdocasProf.get(new Coordinate(i,j,k)), ijk);
                    //trkdvst.addDataSet(Tvstrkdocas.get(new Coordinate(i,j,k)), 0);
                    prfdvst.addDataSet(TvstrkdocasProf.get(new Coordinate(i,j,k)), 0);
                    TvstrkdocasFits.put(new Coordinate(i,j,k), new FitLine());
                    prfdvst.addDataSet(TvstrkdocasFits.get(new Coordinate(i,j,k)), 0);
                    this.getDataGroup().add(prfdvst, 0, i+1, j+1);
                    
                    ijk++;
                }
            }
        }
        //B centroids
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < this.alphaBins; j++) {
                for (int k = 0; k < this.BBins; k++) {
                    B.put(new Coordinate(i,j,k), new H1F("B centroid " +(i + 1)*1000+(j+1)+26, 100, 0.0, 3.0));
                }
            }
        }
        this.getDataGroup().add(td, 0,0,0);
        this.getDataGroup().add(tdp,1,0,0);
        this.getDataGroup().add(cd, 2,0,0);
        this.getDataGroup().add(tr, 3,0,0);
        this.getDataGroup().add(fr, 4,0,0);
        
        for (int i = 0; i < nsl; i++) {
            for (int j = 0; j < alphaBins; j++) {
                this.getCalib().addEntry(0,i+1,j+1);
                //blank out
                this.getCalib().setDoubleValue((double)999, "v0", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "vmid", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "R", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "tmax", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "distbeta", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "delBf", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "b1", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "b2", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "b3", 0, i+1, j+1);
                this.getCalib().setDoubleValue((double)999, "b4", 0, i+1, j+1);
            }
        }
        
        this.getCalib().fireTableDataChanged();
    }
    private void updateTable(int i, int j) {
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(0), "v0", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(1), "vmid", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(2), "R", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(3), "tmax", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(4), "distbeta", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(5), "delBf", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(6), "b1", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(7), "b2", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(8), "b3", 0, i+1, j+1);
        this.getCalib().setDoubleValue(TvstrkdocasFitPars.get(new Coordinate(i)).value(9), "b4", 0, i+1, j+1);
    }    
    @Override
    public void plotHistos() {
        String[] Names = {"TrackDoca vs T","TrackDoca vs T Graphs","CalcDoca vs T","Time Residuals","Parameters"};
        for(int s = 0; s<3; s++) {
            this.getAnalysisCanvas().getCanvas(Names[s]).setGridX(false);
            this.getAnalysisCanvas().getCanvas(Names[s]).setGridY(false);
            int NumPads = 
            this.getAnalysisCanvas().getCanvas(Names[s]).getCanvasPads().size();
            for (int n = 0; n < NumPads; n++) {
                this.getAnalysisCanvas().getCanvas(Names[s]).getPad(n).getAxisZ().setLog(true);
            }
        }
        
        for(int s = 3; s<5; s++) {
            this.getAnalysisCanvas().getCanvas(Names[s]).setGridX(false);
            this.getAnalysisCanvas().getCanvas(Names[s]).setGridY(false);
        }
        this.getAnalysisCanvas().getCanvas(Names[3]).divide(this.nsl, 2);
        this.getAnalysisCanvas().getCanvas(Names[4]).divide(6, 6);
        
        this.getAnalysisCanvas().getCanvas("TrackDoca vs T").update();
        this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").update();
        this.getAnalysisCanvas().getCanvas("CalcDoca vs T").update();
        this.getAnalysisCanvas().getCanvas("Time Residuals").update();
        this.getAnalysisCanvas().getCanvas("Parameters").update();
    }
    @Override
    public void timerUpdate() {
    }
    
    @Override
    public void analysis() {
        writer.close();
        this.UpdateBBinCenters();
        for (int i = 0; i < this.nsl; i++) {
            for (int j = 0; j < this.alphaBins; j++) {
                this.filltrkDocavsTGraphs(i,j);
            }
            
            runFit(i); 
            
        }
        pw.close();
        
        int ij =0;
        int ip =0;
        for (int i = 0; i < this.nsl; i++) {
            //Plot pars
            for(int p = 0; p<6; p++) {

                ParsVsIter.get(new Coordinate(i,p)).setOptStat(0);
                this.getAnalysisCanvas().getCanvas("Parameters").cd(ip);
                GraphErrors gr = new GraphErrors();
                double min = ParsVsIter.get(new Coordinate(i,p)).getMin();
                double max = ParsVsIter.get(new Coordinate(i,p)).getMax();
                if(Math.abs(min)<1.e-06 && Math.abs(max)<1.e-06) {
                    min = -0.1;
                    max = 0.1;
                }
                
                gr= ParsVsIter.get(new Coordinate(i,p)).getGraph();
                gr.addPoint(-1, min-0.1, 0, 0);
                this.getAnalysisCanvas().getCanvas("Parameters").
                        draw(gr);
                
                this.getAnalysisCanvas().getCanvas("Parameters").getPad(ip).getAxisX().setRange(0.5, 11.5);
                ip++;
            }
        }
        
        for (int i = 0; i < this.nsl; i++) {
            
            for (int j = 0; j < this.alphaBins; j++) {
                
                if(i<2 || i>3) {
                    if(TvstrkdocasProf.get(new Coordinate(i, j, BBins)).getVectorX().size()>0) {

                        this.updateTable(i,j);
                        TvstrkdocasFits.put(new Coordinate(i,j,BBins), new FitLine("f"+""+i+""+j+"0", i, j, BBins, 
                        TvstrkdocasFitPars.get(new Coordinate(i))));
                        TvstrkdocasFits.get(new Coordinate(i, j, BBins)).setLineStyle(4);
                        TvstrkdocasFits.get(new Coordinate(i, j, BBins)).setLineWidth(5);
                        TvstrkdocasFits.get(new Coordinate(i, j, BBins)).setLineColor(8);
                    }
                     
                } else {
                    for(int k = 0; k < this.BBins; k++) {
                        if(TvstrkdocasProf.get(new Coordinate(i, j, k)).getVectorX().size()>0){
                            this.updateTable(i,j);
                            TvstrkdocasFits.put(new Coordinate(i,j,k), new FitLine("f"+""+i+""+j+""+k, i, j, k, 
                            TvstrkdocasFitPars.get(new Coordinate(i))));
                            TvstrkdocasFits.get(new Coordinate(i, j, k)).setLineStyle(4);
                            TvstrkdocasFits.get(new Coordinate(i, j, k)).setLineWidth(5);
                            TvstrkdocasFits.get(new Coordinate(i, j, k)).setLineColor(k+1);
                        }
                    }
                }
                ij++;
                
            }
             
        }
        this.getCalib().fireTableDataChanged();    
        this.reProcess();
        this.getCalib().fireTableDataChanged();     
    }
    private int maxIter = 10;
    public void runFit(int i) {
        // i = superlayer - 1;
        
        TvstrkdocasFit.put(new Coordinate(i), 
                new FitFunction(i, (Map<Coordinate, GraphErrors>) TvstrkdocasProf));
        
        MnScan  scanner = new MnScan((FCNBase) TvstrkdocasFit.get(new Coordinate(i)), 
                TvstrkdocasFitPars.get(new Coordinate(i)),2);
	
        for(int p = 0; p<10; p++) {
                ParsVsIter.get(new Coordinate(i,p)).setBinContent(0, TvstrkdocasFitPars.get(new Coordinate(i)).value(p));
                ParsVsIter.get(new Coordinate(i,p)).setBinError(0, TvstrkdocasFitPars.get(new Coordinate(i)).error(p));
            }
        
        for(int p = 5; p<11; p++) {
            scanner.fix(p);
        }
        if(i<2) {
            TvstrkdocasFitPars.get(new Coordinate(i)).setValue(0, 0.0045);
            scanner.fix(2);
            //scanner.fix(2);
        }
        if(i>1 && i<4)
            scanner.fix(3);
        FunctionMinimum scanmin = scanner.minimize();
        if(scanmin.isValid())
            TvstrkdocasFitPars.put(new Coordinate(i),scanmin.userParameters());
        
        MnMigrad migrad = new MnMigrad((FCNBase) TvstrkdocasFit.get(new Coordinate(i)), 
                TvstrkdocasFitPars.get(new Coordinate(i)),1);
        migrad.setCheckAnalyticalDerivatives(true);
        
        FunctionMinimum min ;
        
        
        for(int it = 0; it<maxIter; it++) {
            for(int p = 0; p<10; p++) {
                ParsVsIter.get(new Coordinate(i,p)).setBinContent(it+1, TvstrkdocasFitPars.get(new Coordinate(i)).value(p));
                ParsVsIter.get(new Coordinate(i,p)).setBinError(it+1, TvstrkdocasFitPars.get(new Coordinate(i)).error(p));
            }
            min = migrad.minimize();
            System.err.println("****************************************************");
            System.err.println("*   FIT RESULTS  FOR SUPERLAYER  "+(i+1)+" at iteration "+(it+1)+"  *");
            System.err.println("****************************************************");  
            for(int pi = 0; pi<6; pi++) 
                System.out.println("par["+pi+"]="+(TvstrkdocasFitPars.get(new Coordinate(i)).value(pi)-min.userParameters().value(pi)));
            if(it==2 && (i>1 && i<4))
                migrad.release(5);
            if(it==5 && (i>1 && i<4))
                migrad.release(3);
            if(min.isValid()) {
                TvstrkdocasFitPars.put(new Coordinate(i),min.userParameters());  
            }
            
           
            System.err.println(min);
        }
        
        //Dump results to file
        for(int isec = 0; isec < 6; isec++) {
           
            pw.printf("%d\t %d\t %d\t %.6f\t %d\t %.6f\t %.6f\t %.6f\t %.6f\t %.6f\t %.6f\t %.6f\t %d\t %.6f\t %.6f\t %d\n",
                (isec+1), (i+1), 0,
                TvstrkdocasFitPars.get(new Coordinate(i)).value(0),
                0,
                TvstrkdocasFitPars.get(new Coordinate(i)).value(3),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(4),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(5),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(6),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(7),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(8),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(9),
                0,
                TvstrkdocasFitPars.get(new Coordinate(i)).value(2),
                TvstrkdocasFitPars.get(new Coordinate(i)).value(1),
                0);
        }
        
    }
    int counter = 0;
    public  HipoDataSource reader = new HipoDataSource();
    public void reProcess() {
        
        reader.open("TestOutPut.hipo");
        
        while (reader.hasEvent()) {
            hits.clear();
            DataEvent event = reader.getNextEvent();
            if(event.hasBank("TimeBasedTrkg::TBHits")) {
                DataBank bnkHits = event.getBank("TimeBasedTrkg::TBHits");

                for (int i = 0; i < bnkHits.rows(); i++) {
                    if(this.getHit(bnkHits, i)!=null)
                        hits.add(this.getHit(bnkHits, i));
                }
                // fill uncalibrated plot
                for(FittedHit hit : hits) {
                    fitResi.get(new Coordinate(hit.get_Superlayer()-1)).fill(hit.get_Residual());
                }
                for(FittedHit hit : hits) {
                    hit.set_TimeResidual(-999);
                    updateHit(hit);
                }
                //refit with new constants
                Refit rf = new Refit(hits);
                rf.reFit();
                // fill calibrated plot
                for(FittedHit hit : hits) {
                    timeResi.get(new Coordinate(hit.get_Superlayer()-1)).fill(hit.get_TimeResidual());
                }

            }
        }
        //--------------------------------------------
        System.out.println("reloading Fit Parameters");
        reLoadFitPars();
        //--------------------------------------------
        
        //reset histos to refill
        for (int i = 0; i < this.nsl; i++) {
            for (int j = 0; j < this.alphaBins; j++) {
                for (int k = 0; k < this.BBins; k++) {
                    //TvstrkdocasProf.get(new Coordinate(i,j,k)).reset();
                    Tvstrkdocas.get(new Coordinate(i,j,k)).reset();
                    Tvscalcdocas.get(new Coordinate(i,j,k)).reset();
                }
            }
        }
        
        reader.gotoEvent(0);
        hits.clear();
        while (reader.hasEvent()) {
            DataEvent event = reader.getNextEvent();
            if(event.hasBank("TimeBasedTrkg::TBHits")) {
                DataBank bnkHits = event.getBank("TimeBasedTrkg::TBHits");

                for (int i = 0; i < bnkHits.rows(); i++) {
                    if(this.getHit(bnkHits, i)!=null)
                        hits.add(this.getHit(bnkHits, i));
                }
                
                for(FittedHit hit : hits) {
                    hit.set_TimeResidual(-999);
                    updateHit(hit);
                }
                //refit with new constants
                Refit rf = new Refit(hits);
                rf.reFit();
                // fill calibrated plot
                for(FittedHit hit : rf.hits) {
                    timeResiNew.get(new Coordinate(hit.get_Superlayer()-1)).fill(hit.get_TimeResidual());
                }
                //DataBank bnkHitsUpd = this.fillTBHitsBank(event, rf.hits);
                //this.processEventIterate(bnkHitsUpd);
                hits.clear();
            }
        }
        
        //
        for(int i = 0; i<this.nsl; i++) {
            this.getAnalysisCanvas().getCanvas("Time Residuals").cd(i);
            this.getAnalysisCanvas().getCanvas("Time Residuals").draw(timeResi.get(new Coordinate(i)));
            this.fitTimeResPlot(timeResi.get(new Coordinate(i)), 
                    this.getAnalysisCanvas().getCanvas("Time Residuals"));
            this.getAnalysisCanvas().getCanvas("Time Residuals").cd(i+6);
            this.getAnalysisCanvas().getCanvas("Time Residuals").draw(timeResiNew.get(new Coordinate(i)));
            this.fitTimeResPlot(timeResiNew.get(new Coordinate(i)), 
                    this.getAnalysisCanvas().getCanvas("Time Residuals"));
        }
        //for(int i = 0; i<this.nsl; i++) {
        //    this.getAnalysisCanvas().getCanvas("Fit Residuals").cd(i);
        //    this.getAnalysisCanvas().getCanvas("Fit Residuals").draw(fitResi.get(new Coordinate(i)));
        //}
        
    }
    private void fitTimeResPlot(H1F h1, EmbeddedCanvas canvasRes) {
        F1D gausFunc = new F1D("gausFunc", "[amp]*gaus(x,[mean],[sigma])+[amp2]*gaus(x,[mean],[sigma2])", -0.5, 0.5);
        gausFunc.setLineColor(4);
        gausFunc.setLineStyle(1);
        gausFunc.setLineWidth(2);
        gausFunc.setParameter(0, h1.getMax());
        gausFunc.setParameter(1, -0.0);
        gausFunc.setParameter(2, 0.05);
        gausFunc.setParameter(3, h1.getMax()/2.);
        gausFunc.setParameter(4, 0.5);
        gausFunc.setOptStat(1110);
        h1.setOptStat(0);
        DataFitter.fit(gausFunc, h1, "Q");
        gausFunc.setOptStat(00001111110);
        canvasRes.draw(h1, "same");
    }
    private int getAlphaBin(double alpha) {
        int v = -1;
                //double[] AlphaValues = new double[]{-26,-22,-18,-14,-10,-6,-2,2,6,10,14,18,22,26};
        for(int i = 0; i<AlphaValues.length; i++) {
            if(Math.abs(alpha-AlphaValues[i])<this.AlphaBinHalfWidth)
                v = i;
        }      
        return v;
    }

    private int getBBin(double bFieldVal) {
        
        int v = BfieldValues.length-1;
        //BfieldValues = new double[]{0.0000, 1.0000, 1.4142, 1.7321, 2.0000, 2.2361, 2.4495, 2.6458};
        //BfieldValues^2 = new double[]{0.0000, 1.0000, 2.0000, 3.0000, 4.0000, 5.0000, 6.0000, 7.0000};
        double BSqrBinHalfWidth = 0.5;
        for(int i = 0; i<BfieldValues.length; i++) {
            if(Math.abs(bFieldVal*bFieldVal-this.BfieldValues[i]*this.BfieldValues[i])<BSqrBinHalfWidth)
                v = i;
        }      
        
        //return bbinIdx ;
        return v ;
    }
     private int MINENTRIES = 10;
    F1D f1 = new F1D("f1","[amp]*gaus(x,[mean],[sigma])+[p0]", 0, 1.8);
    
    private void filltrkDocavsTGraphs(int i, int j, int k) {
        
        if(TvstrkdocasProf.get(new Coordinate(i, j, k))!=null) {
            
            TvstrkdocasProf.get(new Coordinate(i, j, k)).reset();
            H2F h2 = Tvstrkdocas.get(new Coordinate(i, j, k));
            ArrayList<H1F> hslice = h2.getSlicesX();
            
            for(int si=0; si<hslice.size(); si++) {
                double amp   = hslice.get(si).getBinContent(hslice.get(si).getMaximumBin());
                
                if(amp<this.MINENTRIES) {
                    
                } else {
                    double x = h2.getXAxis().getBinCenter(si);
                    double y = hslice.get(si).getMean();
                    double sigma = hslice.get(si).getRMS();
                    
                    f1.setParameter(0, amp);
                    f1.setParameter(1, y);
                    f1.setParameter(2, sigma);
                    f1.setParameter(3, 0);
                    DataFitter.fit(f1, hslice.get(si), "Q"); //No options uses error for sigma 
                    if(f1.getChiSquare()<100 && f1.getParameter(1)>0 && f1.parameter(1).error()<50)
                        TvstrkdocasProf.get(new Coordinate(i, j, k)).
                                addPoint(x, f1.getParameter(1), 0, f1.getParameter(2));//f1.parameter(1).error()
                }
            }
        }
    }

    
    private void filltrkDocavsTGraphs(int i, int j) {
        if(i<2 || i>3) { //region 1 and 3
            filltrkDocavsTGraphs(i, j, BBins);
        } else {
            for(int k = 0; k < this.BBins; k++) {
                filltrkDocavsTGraphs(i, j, k);
            }
        }       
    }


    int count = 0;
    public static int polarity =-1;
    public List<FittedHit> hits = new ArrayList<>();
    
    List<FittedHit> hitlist = new ArrayList<>();
    @Override
    public void processEvent(DataEvent event) {
        if (!event.hasBank("RUN::config")) {
            return ;
        }
        
        DataBank bank = event.getBank("RUN::config");
        int newRun = bank.getInt("run", 0);
       if (newRun == 0) {
           return ;
       } else {
           count++;
       }
       
       if(count==1) {
            Constants.Load();
            TableLoader.FillT0Tables(newRun, "default");
            TableLoader.Fill(Viewer.ccdb.getConstants(newRun, Constants.TIME2DIST));  
            this.loadFitPars();
            polarity = (int)Math.signum(event.getBank("RUN::config").getFloat("torus",0));
       
       }
        if(!event.hasBank("TimeBasedTrkg::TBHits")) {
            return;
        } 
        
        DataBank bnkHits = event.getBank("TimeBasedTrkg::TBHits");
        for (int i = 0; i < bnkHits.rows(); i++) {
            

                double bFieldVal = (double) bnkHits.getFloat("B", i);
                int superlayer = bnkHits.getInt("superlayer", i);
                
                //int region = (int) (superlayer + 1) / 2;
                double alpha = bnkHits.getFloat("Alpha", i);
                int alphaBin = this.getAlphaBin(alpha);
                boolean passHit = false;

//                    if( ( ( region ==1 && alpha> -20.0 && alpha< -5.0)
//                    || (region ==2 && alpha> -20.0 && alpha< 0.0)
//                    || (region ==3 && alpha> -28.0 && alpha< 2.0) )) {
//                        passHit = true;
//                    }
            if (bnkHits.getByte("trkID", i) >0 && bnkHits.getFloat("beta", i)>0.9 && bnkHits.getFloat("TFlight", i)>0 && Math.abs(bnkHits.getFloat("fitResidual", i))<0.075)
            {
                
                hitlist.add(this.getHit(bnkHits, i));
                double calibTime = (double) (bnkHits.getInt("TDC", i) - bnkHits.getFloat("TProp", i)
                                        - bnkHits.getFloat("TFlight", i) - bnkHits.getFloat("TStart", i) 
                                        - bnkHits.getFloat("T0", i) -0*bnkHits.getFloat("tBeta", i));
                
//                    double calibTime = this.computeCalibTime(bnkHits.getInt("sector", i), 
//                            bnkHits.getInt("superlayer", i), 
//                            bnkHits.getFloat("trkDoca", i), 
//                            bnkHits.getInt("TDC", i), 
//                            bnkHits.getFloat("TProp", i),
//                            bnkHits.getFloat("TFlight", i), 
//                            bnkHits.getFloat("TStart", i),bnkHits.getFloat("T0", i), bnkHits.getFloat("tBeta", i));
                //fill all B bins (index = 7)
                Tvstrkdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, this.BBins))
                                .fill(bnkHits.getFloat("trkDoca", i), calibTime);
                Tvscalcdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, this.BBins))
                                .fill(bnkHits.getFloat("doca", i), calibTime);
                //Fill region 2 for different b-field values
                if(superlayer>2 && superlayer<5) { 
                    int bBin = this.getBBin(bFieldVal);
                    Tvstrkdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, bBin))
                                .fill(bnkHits.getFloat("trkDoca", i), calibTime);
                    Tvscalcdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, bBin))
                                .fill(bnkHits.getFloat("doca", i), calibTime);
                    // fill B values histograms
                    if(superlayer ==3 || superlayer ==4)
                        B.get(new Coordinate(superlayer-3, alphaBin, bBin))
                                .fill(bFieldVal);
                }
            }
        }
        hipoEvent = (HipoDataEvent) writer.createEvent();
        DataBank selected = this.fillTBHitsBank(event, hitlist);
        //selected.show();
        hipoEvent.appendBank(selected);
        writer.writeEvent(hipoEvent);
        hitlist.clear();
    }
    public void processEventIterate(DataBank bnkHits) {
        
        for (int i = 0; i < bnkHits.rows(); i++) {
            if (bnkHits.getByte("trkID", i) >= 1 
                    ) {

                double bFieldVal = (double) bnkHits.getFloat("B", i);
                int superlayer = bnkHits.getInt("superlayer", i);
                
                double alpha = bnkHits.getFloat("Alpha", i);
                int alphaBin = this.getAlphaBin(alpha);
               
                double calibTime = (double) (bnkHits.getInt("TDC", i) - bnkHits.getFloat("TProp", i)
                                        - bnkHits.getFloat("TFlight", i) - bnkHits.getFloat("TStart", i) 
                                        - bnkHits.getFloat("T0", i) -0*bnkHits.getFloat("tBeta", i));
                //fill all B bins (index = 7)
                Tvstrkdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, this.BBins))
                                .fill(bnkHits.getFloat("trkDoca", i), calibTime);
                Tvscalcdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, this.BBins))
                                .fill(bnkHits.getFloat("doca", i), calibTime);
                
                if(superlayer<3 || superlayer>4) {
                    int bBin = this.getBBin(bFieldVal);
                    Tvstrkdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, bBin))
                                .fill(bnkHits.getFloat("trkDoca", i), calibTime);
                    Tvscalcdocas.get(new Coordinate(bnkHits.getInt("superlayer", i) - 1, alphaBin, bBin))
                                .fill(bnkHits.getFloat("doca", i), calibTime);
                }

            }
        }
        //hipoEvent = (HipoDataEvent) writer.createEvent();
        //hipoEvent.appendBank(bnkHits);
        //writer.writeEvent(hipoEvent);
        
    }
    private String[] parNames = {"v0", "vmid", "R", "tmax", "distbeta", "delBf", 
        "b1", "b2", "b3", "b4", "dmax"};
    private double[] errs = {0.001,0.001,0.01,1.0,0.01,0.001,0.001,0.001,0.001,0.001,0.00001};
    public void loadFitPars() {
        for (int i = 0; i < this.nsl; i++) {
            double[] pars = new double[11];
            //T2DFunctions.polyFcnMac(x, alpha, bfield, v0[s][r], vmid[s][r], FracDmaxAtMinVel[s][r], 
            //tmax, dmax, delBf, Bb1, Bb2, Bb3, Bb4, superlayer) ;
            pars[0] = TableLoader.v0[0][i];
            pars[1] = TableLoader.vmid[0][i];
            pars[2] = TableLoader.FracDmaxAtMinVel[0][i];
            pars[3] = TableLoader.Tmax[0][i];
            pars[4] = TableLoader.distbeta[0][i];
            pars[5] = TableLoader.delta_bfield_coefficient[0][i];
            pars[6] = TableLoader.b1[0][i];
            pars[7] = TableLoader.b2[0][i];
            pars[8] = TableLoader.b3[0][i];
            pars[9] = TableLoader.b4[0][i];
            pars[10] = 2.*Constants.wpdist[i];//fix dmax
            TvstrkdocasFitPars.put(new Coordinate(i), new MnUserParameters());
            for(int p = 0; p < 10; p++) {
                TvstrkdocasFitPars.get(new Coordinate(i)).add(parNames[p], pars[p], errs[p]);
                //create graphs of parameters for various iterations
                ParsVsIter.put(new Coordinate(i,p), new H1F("h"+p, "superlayer "+(i+1)+" par "+p,this.maxIter+1, 0.5,this.maxIter+1.5));
            }
            TvstrkdocasFitPars.get(new Coordinate(i)).add(parNames[10], pars[10], errs[10]);
            
        }   
    }
    private void reLoadFitPars() {
        for (int s =0; s < 6; s++) {
            for (int i = 0; i < this.nsl; i++) {
                TableLoader.v0[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(0);
                TableLoader.vmid[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(1);
                TableLoader.FracDmaxAtMinVel[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(2);
                System.out.println("TMAX "+TableLoader.Tmax[s][i]+" ==> ");
                TableLoader.Tmax[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(3);
                System.out.println(".................... "+TableLoader.Tmax[s][i]+"");
                TableLoader.distbeta[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(4);
                TableLoader.delta_bfield_coefficient[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(5);
                TableLoader.b1[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(6);
                TableLoader.b2[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(7);
                TableLoader.b3[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(8);
                TableLoader.b4[s][i] = TvstrkdocasFitPars.get(new Coordinate(i)).value(9);
            }
        }
        TableLoader.ReFill();
    }
    
    public void Plot(int i , int j) {
        
        if(i<2 || i>3) { // regions 1 and 3 --> no b-field
            if(TvstrkdocasProf.get(new Coordinate(i, j, BBins)).getVectorX().size()>0) {
                this.getAnalysisCanvas().getCanvas("TrackDoca vs T").cd(0);
                this.getAnalysisCanvas().getCanvas("TrackDoca vs T").draw(Tvstrkdocas.get(new Coordinate(i, j, BBins)));
                this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").cd(0);
                this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").draw(Tvstrkdocas.get(new Coordinate(i, j, BBins)));
                this.getAnalysisCanvas().getCanvas("CalcDoca vs T").cd(0);
                this.getAnalysisCanvas().getCanvas("CalcDoca vs T").draw(Tvscalcdocas.get(new Coordinate(i, j, BBins)));
                this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").
                        draw(TvstrkdocasProf.get(new Coordinate(i, j, BBins)), "same");
                this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").
                        draw(TvstrkdocasFits.get(new Coordinate(i, j, BBins)), "same");
                this.getAnalysisCanvas().getCanvas("CalcDoca vs T").
                        draw(TvstrkdocasFits.get(new Coordinate(i, j, BBins)), "same");
            }
        } else {   
            //plot the profiles for the various B-field components
            
            this.getAnalysisCanvas().getCanvas("TrackDoca vs T").cd(0);
            this.getAnalysisCanvas().getCanvas("TrackDoca vs T").draw(Tvstrkdocas.get(new Coordinate(i, j, BBins)));
            this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").cd(0);
            this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").draw(Tvstrkdocas.get(new Coordinate(i, j, BBins)));
            this.getAnalysisCanvas().getCanvas("CalcDoca vs T").cd(0);
            this.getAnalysisCanvas().getCanvas("CalcDoca vs T").draw(Tvscalcdocas.get(new Coordinate(i, j, BBins)));    

            for(int k = 0; k < this.BBins; k++) {
                if(TvstrkdocasProf.get(new Coordinate(i, j, k)).getVectorX().size()>0){
                    this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").
                            draw(TvstrkdocasProf.get(new Coordinate(i, j, k)), "same");
                    this.getAnalysisCanvas().getCanvas("TrackDoca vs T Graphs").
                            draw(TvstrkdocasFits.get(new Coordinate(i, j, k)), "same");
                    this.getAnalysisCanvas().getCanvas("CalcDoca vs T").
                    draw(TvstrkdocasFits.get(new Coordinate(i, j, k)), "same");
                }
            }
            
        }
    }
    @Override
    public void constantsEvent(CalibrationConstants cc, int col, int row) {
        String str_sector    = (String) cc.getValueAt(row, 0);
        String str_layer     = (String) cc.getValueAt(row, 1);
        String str_component = (String) cc.getValueAt(row, 2);
        System.out.println(str_sector + " " + str_layer + " " + str_component);
        IndexedList<DataGroup> group = this.getDataGroup();

       int sector    = Integer.parseInt(str_sector);
       int layer     = Integer.parseInt(str_layer);
       int component = Integer.parseInt(str_component);

       if(group.hasItem(sector,layer,component)==true){
           this.Plot(layer-1, component-1);
       } else {
           System.out.println(" ERROR: can not find the data group");
       }
       
   
    }

    private FittedHit getHit(DataBank bnkHits, int i) {
        FittedHit hit = null;
        int id = bnkHits.getShort("id", i);;
        int sector = bnkHits.getByte("sector", i);
        int superlayer = bnkHits.getByte("superlayer", i);
        int layer = bnkHits.getByte("layer", i);
        int wire = bnkHits.getShort("wire", i);
        int TDC = bnkHits.getInt("TDC", i);
        double doca = bnkHits.getFloat("doca", i);
        double docaError = bnkHits.getFloat("docaError", i);
        double trkDoca = bnkHits.getFloat("trkDoca", i);
        int LR = bnkHits.getByte("LR", i);
        double X = bnkHits.getFloat("X", i);
        double Z = bnkHits.getFloat("Z", i);
        double B = bnkHits.getFloat("B", i);
        double Alpha = bnkHits.getFloat("Alpha", i);
        double TProp = bnkHits.getFloat("TProp", i);
        double TFlight = bnkHits.getFloat("TFlight", i);
        double T0 = bnkHits.getFloat("T0", i);
        double TStart = bnkHits.getFloat("TStart", i);
        int clusterID = bnkHits.getShort("clusterID", i);
        int trkID = bnkHits.getByte("trkID", i);
        double time = bnkHits.getFloat("time", i);
        double beta = bnkHits.getFloat("beta", i);
        double tBeta = bnkHits.getFloat("tBeta", i);
        double resiTime = bnkHits.getFloat("timeResidual", i);
        double resiFit = bnkHits.getFloat("fitResidual", i);
         
        hit = new FittedHit(sector, superlayer, layer, wire, TDC, id);
        hit.set_Id(id); // use event number as id to recompose the clusters
        hit.setB(B);
        hit.setT0(T0);
        hit.setTStart(TStart);
        hit.setTProp(TProp);
        hit.set_Beta(beta);
        hit.setTFlight(TFlight);
        double T0Sub = (TDC - TProp - TFlight - T0);
        hit.set_Time(T0Sub-TStart);
        hit.set_LeftRightAmb(LR);
        hit.calc_CellSize(Viewer.dcDetector);
        hit.set_X(X);
        hit.set_Z(Z);
        hit.calc_GeomCorr(Viewer.dcDetector, 0);
        hit.set_ClusFitDoca(trkDoca);
        hit.set_DeltaTimeBeta(tBeta);
        hit.set_Doca(doca);
        hit.set_Time(time);
        hit.setAlpha(Alpha);
        hit.set_DocaErr(docaError);
        hit.set_AssociatedClusterID(clusterID);
        hit.set_AssociatedHBTrackID(trkID); 
        hit.set_TimeResidual(resiTime);
        hit.set_Residual(resiFit);
        
        return hit;
    }
    private DataBank fillTBHitsBank(DataEvent event, List<FittedHit> hitlist) {
        //if(event.hasBank("TimeBasedTrkg::TBHits")) { // for second pass tracking
        //     event.removeBank("TimeBasedTrkg::TBHits");
        //}
        DataBank bank = event.createBank("TimeBasedTrkg::TBHits", hitlist.size());

        for (int i = 0; i < hitlist.size(); i++) {
            
            bank.setShort("id", i, (short) hitlist.get(i).get_Id());
            bank.setShort("status", i, (short) hitlist.get(i).get_QualityFac());
            bank.setByte("superlayer", i, (byte) hitlist.get(i).get_Superlayer());
            bank.setByte("layer", i, (byte) hitlist.get(i).get_Layer());
            bank.setByte("sector", i, (byte) hitlist.get(i).get_Sector());
            bank.setShort("wire", i, (short) hitlist.get(i).get_Wire());

            bank.setFloat("X", i, (float) hitlist.get(i).get_X());
            bank.setFloat("Z", i, (float) hitlist.get(i).get_Z());
            bank.setByte("LR", i, (byte) hitlist.get(i).get_LeftRightAmb());
            bank.setFloat("time", i, (float) (hitlist.get(i).get_Time() + 0*hitlist.get(i).get_DeltaTimeBeta()));
            bank.setFloat("tBeta", i, (float) hitlist.get(i).get_DeltaTimeBeta());
            bank.setFloat("fitResidual", i, (float) hitlist.get(i).get_Residual());
            bank.setFloat("Alpha", i, (float) hitlist.get(i).getAlpha());
            
            bank.setFloat("doca", i, (float) hitlist.get(i).get_Doca());
            bank.setFloat("docaError", i, (float) hitlist.get(i).get_DocaErr());
            bank.setFloat("trkDoca", i, (float) hitlist.get(i).get_ClusFitDoca());

            bank.setShort("clusterID", i, (short) hitlist.get(i).get_AssociatedClusterID());
            bank.setByte("trkID", i, (byte) hitlist.get(i).get_AssociatedHBTrackID());
            bank.setFloat("timeResidual", i, (float) hitlist.get(i).get_TimeResidual());
            
            bank.setInt("TDC",i,hitlist.get(i).get_TDC());
            bank.setFloat("B", i, (float) hitlist.get(i).getB());
            bank.setFloat("TProp", i, (float) hitlist.get(i).getTProp());
            bank.setFloat("TFlight", i, (float) hitlist.get(i).getTFlight());
            bank.setFloat("T0", i, (float) hitlist.get(i).getT0());
            bank.setFloat("TStart", i, (float) hitlist.get(i).getTStart());
            
            bank.setFloat("beta", i, (float) hitlist.get(i).get_Beta());
            
            

        }
        //System.out.println(" Created Bank "); bank.show();
        return bank;

    }
    private void updateHit(FittedHit hit) {
        double distbeta = TvstrkdocasFitPars.get(new Coordinate(hit.get_Superlayer()-1)).value(4);
        double deltatime_beta = (Math.sqrt(hit.get_ClusFitDoca() * hit.get_ClusFitDoca() 
                + (distbeta * hit.get_Beta() * hit.get_Beta()) 
                * (distbeta* hit.get_Beta() * hit.get_Beta())) - hit.get_ClusFitDoca()) / Constants.V0AVERAGED;
        hit.set_DeltaTimeBeta(deltatime_beta);
        hit.set_Doca(this.timeToDistance(hit.get_Sector(), hit.get_Superlayer(), 
                hit.getAlpha(), hit.get_Beta(), hit.getB(), hit.get_ClusFitDoca(), hit.get_Time(), distbeta));
        double x = hit.get_XWire();
        double cosTkAng = Math.cos(Math.toRadians(hit.getAlpha()));
        hit.set_X(x + hit.get_LeftRightAmb() * (hit.get_Doca() / cosTkAng) );
        
    }
    
    private TimeToDistanceEstimator tde = new TimeToDistanceEstimator();
    private double timeToDistance(int sector, int superlayer, double alpha, double beta, double B, double x, double time,
                                double distbeta) {
        double distance = 0;
        //local angle correction
        double theta0 = Math.toDegrees(Math.acos(1-0.02*B));
        alpha-=(double)PlotMaker.polarity*theta0;
        //reduce the corrected angle
        double ralpha = (double) TvstrkdocasFit.get(new Coordinate(0)).getReducedAngle(alpha);
            
        if(beta>1.0)
            beta = 1.0;
        double deltatime_beta = (Math.sqrt(x * x + (distbeta * beta * beta) 
                * (distbeta* beta * beta)) - x) / Constants.V0AVERAGED;
        double correctedTime = time -deltatime_beta;
        if(correctedTime<=0)
            correctedTime=0.01; // fixes edge effects ... to be improved
        distance = tde.interpolateOnGrid(B, ralpha, 
                        correctedTime, 
                        sector-1, superlayer-1) ;
        return distance;
    }

    private void UpdateBBinCenters() {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < this.alphaBins; j++) {
                for (int k = 0; k < this.BBins; k++) {
                    BfieldValuesUpd[i][j][k] = BfieldValues[k];
                    if(B.get(new Coordinate(i,j,k)).getBinContent(B.get(new Coordinate(i,j,k)).getMaximumBin())>10)
                        BfieldValuesUpd[i][j][k] = B.get(new Coordinate(i,j,k)).getMean();
                    System.out.println("ijk" +i+" "+j+" "+k+" BBin UPdated "+BfieldValues[k]+" --> "+BfieldValuesUpd[i][j][k] );
                }
            }
        }
    }
    
}
