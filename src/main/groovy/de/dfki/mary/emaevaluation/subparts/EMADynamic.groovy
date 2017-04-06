package de.dfki.mary.emaevaluation.subparts

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import org.gradle.api.Project
import java.util.regex.*
import de.dfki.mary.ttsanalysis.AnalysisInterface

import marytts.analysis.distances.acoustic.*;
import marytts.analysis.distances.graphic.*;
import marytts.analysis.alignment.IDAlignment;

class EMADynamicAnalysis implements AnalysisInterface
{
    public void addTasks(Project project)
    {

        project.task("computeRMSEEMADyn") {

            def output_handles = []
            project.configurationEMA.channel_labels.each { c ->
                output_handles << new File("${project.configurationEMA.output_dir}/rmse_dyn_${c}.csv")
                outputs.files output_handles
            }

            doLast {
                output_handles.each {f ->
                    f.text = "# RMSE (dyn)\n"
                }
                project.configurationEMA.list_basenames.each { line ->
                    // Load files
                    double[][] src =
                        project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.reference_dir['ema']}/${line}.ema",
                                                        project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size
                    double[][] tgt =
                        project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.synthesize_dir['ema']}/${line}.ema",
                                                        project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size


                    int nb_frames = Math.min(src.length, tgt.length)
                    for (int j=0; j<project.configurationEMA.channels.size()*3; j+=3)  // FIXME: hardcoded frame size
                    {
                        double[][] real_src = new double[nb_frames][3];
                        double[][] real_tgt = new double[nb_frames][3];

                        for (int i=0; i<nb_frames; i++)
                        {
                            if (i == 0)
                            {
                                for (int k=0; k<3; k++)
                                {
                                    real_src[i][k] =  0.5*src[i+1][j+k]
                                    real_tgt[i][k] =  0.5*tgt[i+1][j+k]
                                }
                            }
                            else if (i >= (nb_frames-1))
                            {
                                for (int k=0; k<3; k++)
                                {
                                    real_src[i][k] = -0.5*src[i-1][j+k]
                                    real_tgt[i][k] = -0.5*tgt[i-1][j+k]
                                }
                            }
                            else
                            {
                                for (int k=0; k<3; k++)
                                {
                                    real_src[i][k] = -0.5*src[i-1][j+k] + 0.5*src[i+1][j+k]
                                    real_tgt[i][k] = -0.5*tgt[i-1][j+k] + 0.5*tgt[i+1][j+k]
                                }
                            }
                        }


                        def v = new RMS(real_src, real_tgt, 3); // FIXME: hardcoded stuff
                        for (int i=0; i<nb_frames; i++)
                        {
                            output_handles[(j/3).intValue()] << v.distancePerFrame(i, i) << "\n"
                        }
                    }
                }
            }
        }


        project.task('computeRMSEEMADynWithoutSilence') {
            dependsOn "configurationEMA"

            def output_handles = []
            project.configurationEMA.channel_labels.each { c ->
                output_handles << new File("${project.configurationEMA.output_dir}/rmse_dyn_no_sil_${c}.csv")
                outputs.files output_handles
            }

            doLast {
                output_handles.each {f ->
                    f.text = "# RMSE (dyn)\n"
                }



                project.configurationEMA.list_basenames.each { line ->

                    // FIXME: stupid weight again
                    double[][] src;
                    double[][] tgt;
                    src = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.reference_dir['ema']}/${line}.ema", project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size
                    tgt = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.synthesize_dir['ema']}/${line}.ema", project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size

                    // Compute size
                    def ref_dur_list = []
                    int nb_frames = 0
                    (new File("${project.configurationEMA.lab_dir}/${line}.lab")).eachLine { label -> // FIXME: hardcoded reference name
                        def elts = label.split()

                        if (elts[2] != "pau")
                        {
                            def start = (elts[0].toInteger() / (10000 * 5)).intValue()
                            def end = (elts[1].toInteger() / (10000 * 5)).intValue()
                            nb_frames = nb_frames + (end-start)
                        }
                    }

                    for (int j=0; j<project.configurationEMA.channels.size()*3; j+=3)
                    {
                        double[][] real_src = new double[nb_frames][3];
                        double[][] real_tgt = new double[nb_frames][3];
                        int start_offset = 0;

                        (new File("${project.configurationEMA.lab_dir}/${line}.lab")).eachLine { label -> // FIXME: hardcoded reference name
                            def elts = label.split()

                            if (elts[2] != "pau")
                            {
                                def start = (elts[0].toInteger() / (10000 * 5)).intValue()
                                def end = (elts[1].toInteger() / (10000 * 5)).intValue()
                                int cur_nb_frames = end - start;
                                for (int i=0; i<cur_nb_frames; i++)
                                {
                                    if (i==0)
                                    {
                                        for (int k=0; k<3; k++)
                                        {
                                            real_src[start_offset+i][k] = 0.5*src[start_offset+i+1][j+k]
                                            real_tgt[start_offset+i][k] = 0.5*tgt[start_offset+i+1][j+k]
                                        }
                                    }
                                    else if (i >= (cur_nb_frames-1))
                                    {
                                        for (int k=0; k<3; k++)
                                        {
                                            real_src[start_offset+i][k] = -0.5*src[start_offset+i-1][j+k]
                                            real_tgt[start_offset+i][k] = -0.5*tgt[start_offset+i-1][j+k]
                                        }
                                    }
                                    else
                                    {
                                        for (int k=0; k<3; k++)
                                        {
                                            real_src[start_offset+i][k] = -0.5*src[start_offset+i-1][j+k] + 0.5*src[start_offset+i+1][j+k]
                                            real_tgt[start_offset+i][k] = -0.5*tgt[start_offset+i-1][j+k] + 0.5*tgt[start_offset+i+1][j+k]
                                        }
                                    }
                                }

                                start_offset = start_offset + cur_nb_frames
                            }
                        }

                        def v = new RMS(real_src, real_tgt, 3); // FIXME: hardcoded stuff
                        for (int i=0; i<nb_frames; i++)
                        {
                            output_handles[(j/3).intValue()] << v.distancePerFrame(i, i) << "\n"
                        }
                    }
                }
            }
        }

        project.task('computeEucDistEMADynPerPhoneme') {
            dependsOn "configurationEMA"
            ext.output_f = new File("${project.configurationEMA.output_dir}/rmse_ema_dyn_per_phoneme.csv")
            outputs.files output_f

            doLast {
                // Initialisation
                def val = []
                output_f.withWriter('UTF-8') { writer ->
                    writer.write("#utt\tid_frame\tlabel\tcoil\tRMSE(dyn)\n")
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
                                    if (i == 0)
                                    {
                                        for (int k=0; k<3; k++)
                                        {
                                            real_src[i][k] =  0.5*src[i+1][j+k]
                                            real_tgt[i][k] =  0.5*tgt[i+1][j+k]
                                        }
                                    }
                                    else if (i >= (nb_frames-1))
                                    {
                                        for (int k=0; k<3; k++)
                                        {
                                            real_src[i][k] = -0.5*src[i-1][j+k]
                                            real_tgt[i][k] = -0.5*tgt[i-1][j+k]
                                        }
                                    }
                                    else
                                    {
                                        for (int k=0; k<3; k++)
                                        {
                                            real_src[i][k] = -0.5*src[i-1][j+k] + 0.5*src[i+1][j+k]
                                            real_tgt[i][k] = -0.5*tgt[i-1][j+k] + 0.5*tgt[i+1][j+k]
                                        }
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
