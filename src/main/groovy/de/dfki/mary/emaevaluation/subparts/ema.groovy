package de.dfki.mary.emaevaluation.subparts

import org.gradle.api.Project

import de.dfki.mary.ttsanalysis.AnalysisInterface

import marytts.analysis.distances.acoustic.*;
import marytts.analysis.alignment.IDAlignment;

class EMAAnalysis implements AnalysisInterface
{
    public void addTasks(Project project)
    {

        project.task("computeEMARMSE")
        {
            doLast {
                output_f.text = "#id\tmcdist\n"

                project.list_file.eachLine { line ->


                    def nb_frames = Math.min(src.length, tgt.length)
                }
            }
        }

        project.task("computeRMSEEMA") {

            // FIXME: input file ?
            def output_f = new File("${project.emaOutputDir}/rms_ema.csv")
            outputs.files output_f

            doLast {
                output_f.text = "#id\trmse (mm)\n"

                list_file.eachLine { line ->
                    // Load files
                    double[][] src =
                        project.loading.loadFloatBinary("${project.referenceDir['ema']}/${line}.ema",
                                                        self.channels.size()*3); // FIXME: hardcoded frame size
                    double[][] tgt =
                        project.loading.loadFloatBinary("${project.synthesizeDir['ema']}/${line}.ema",
                                                        self.channels.size()*3); // FIXME: hardcoded frame size

                    def nb_frames = Math.min(src.length, tgt.length)


                    // Compute and dump the distance
                    def alignment = new IDAlignment(nb_frames);
                    def v = new RMS(src, tgt, self.channels.size()*3); // FIXME: hardcoded frame size
                    def v = new CepstralDistorsion(src, tgt, project.mgcDim);
                    Double d = v.distancePerUtterance(alignment);
                    output_f << "$line\t$d\n";
                }
            }
        }

        task computeEucDistEMA() {

            def output_handles = []
            self.channels.each { c ->
                output_handles << new File("${project.emaOutputDir}/euc_dist_${c}.csv")
                outputs.files output_handles
            }

            doLast {
                output_handles.each {f ->
                    f.text = "# euc. dist. (cm)\n"
                }
                def loading = new LoadingHelpers();
                list_file.eachLine { line ->
                    // Load files
                    double[][] src =
                        project.loading.loadFloatBinary("${project.referenceDir['ema']}/${line}.ema",
                                                        self.channels.size()*3); // FIXME: hardcoded frame size
                    double[][] tgt =
                        project.loading.loadFloatBinary("${project.synthesizeDir['ema']}/${line}.ema",
                                                        self.channels.size()*3); // FIXME: hardcoded frame size


                    int nb_frames = Math.min(src.length, tgt.length)
                    for (int j=0; j<self.channels.size()*3; j+=3)  // FIXME: hardcoded frame size
                    {
                        double[][] real_src = new double[nb_frames][3];
                        double[][] real_tgt = new double[nb_frames][3];

                        for (int i=0; i<nb_frames; i++)
                        {
                            for (int k=0; k<3; k++)
                            {
                                real_src[i][k] = src[i][j+k]
                                real_tgt[i][k] = tgt[i][j+k] / 10 // FIXME: hardcoded mm => cm
                            }
                        }


                        def v = new EuclidianDistance(real_src, real_tgt, 3); // FIXME: hardcoded stuff
                        for (int i=0; i<nb_frames; i++)
                        {
                            output_handles[(j/3).intValue()] << v.distancePerFrame(i, i) << "\n"
                        }
                    }
                }
            }
        }
    }
}