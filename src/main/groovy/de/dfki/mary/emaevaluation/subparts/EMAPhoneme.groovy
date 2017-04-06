package de.dfki.mary.emaevaluation.subparts

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import org.gradle.api.Project
import java.util.regex.*
import de.dfki.mary.ttsanalysis.AnalysisInterface

import marytts.analysis.distances.acoustic.*;
import marytts.analysis.distances.graphic.*;
import marytts.analysis.alignment.IDAlignment;

class EMAPhonemeAnalysis implements AnalysisInterface
{
    public void addTasks(Project project)
    {
        project.task('computeEucDistEMAPerPhoneme') {
            dependsOn "configurationEMA"
            ext.output_f = new File("${project.configurationEMA.output_dir}/dist_euc_ema_per_phoneme.csv")
            outputs.files output_f

            doLast {
                // Initialisation
                def val = []
                output_f.withWriter('UTF-8') { writer ->
                    writer.write("#utt\tid_frame\tlabel\tcoil\teuc_dist(mm)\n")
                    project.configurationEMA.list_basenames.each { line ->

                        // FIXME: stupid weight again
                        double[][] src;
                        double[][] tgt;
                        src = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.reference_dir['ema']}/${line}.ema", project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size
                        tgt = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.synthesize_dir['ema']}/${line}.ema", project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size

                        // Loading first label
                        def ref_dur_list = []
                        (new File("${project.configurationEMA.lab_dir}/${line}.lab")).eachLine { label -> // FIXME: hardcoded reference name
                            def elts = label.split()

                            def start = (elts[0].toInteger() / (10000 * 5)).intValue()
                            def end = (elts[1].toInteger() / (10000 * 5)).intValue()

                            // FIXME: hardcoded frame size
                            int nb_frames = end - start
                            for (int j=0; j<project.configurationEMA.channels.size()*3; j+=3)
                            {
                                double[][] real_src = new double[nb_frames][3];
                                double[][] real_tgt = new double[nb_frames][3];
                                for (int i=0; i<nb_frames; i++)
                                {
                                    for (int k=0; k<3; k++)
                                    {
                                        real_src[i][k] = src[i][j+k]
                                        real_tgt[i][k] = tgt[i][j+k]
                                    }
                                }

                                def v = new EuclidianDistance(real_src, real_tgt, 3);

                                for (int i=0; i<nb_frames; i++)
                                {
                                    writer.write(line + "\t" + (start + i) + "\t" + elts[2]  + "\t" + project.configurationEMA.channel_labels[(j/3).intValue()] + "\t" + v.distancePerFrame(i, i) + "\n")
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}