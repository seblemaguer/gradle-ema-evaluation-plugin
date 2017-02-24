package de.dfki.mary.emaevaluation.subparts

import org.gradle.api.Project

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
            def raw_output_f = new File("${project.configurationEMA.output_dir}/dist_euc_ema_per_phoneme.csv")
            outputs.files raw_output_f

            doLast {
                // Initialisation
                def val = []
                raw_output_f.withWriter('UTF-8') { writer ->
                    writer.write("#utt\tid_frame\tlabel\tcoil\teuc_dist(mm)\n")
                    list_file.eachLine { line ->

                        // FIXME: stupid weight again
                        double[][] src;
                        double[][] tgt;
                        src = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.reference_dir['ema']}/${line}.ema",channels.size()*3); // FIXME: hardcoded frame size
                        tgt = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.synthesize_dir['ema']}/${line}.ema", channels.size()*3); // FIXME: hardcoded frame size

                        // Loading first label
                        def ref_dur_list = []
                        (new File("${project.configurationEMA.reference_dir['dur']}/${line}.lab")).eachLine { label -> // FIXME: hardcoded reference name
                            def elts = label.split()

                            def start = (elts[0].toInteger() / (10000 * 5)).intValue()
                            def end = (elts[1].toInteger() / (10000 * 5)).intValue()

                            // FIXME: hardcoded frame size
                            int nb_frames = end - start
                            for (int j=0; j<channels.size()*3; j+=3)
                            {
                                double[][] real_src = new double[nb_frames][3];
                                double[][] real_tgt = new double[nb_frames][3];
                                for (int i=0; i<nb_frames; i++)
                                {
                                    for (int k=0; k<3; k++)
                                    {
                                        real_src[i][k] = src[i][j+k]
                                        real_tgt[i][k] = tgt[i][j+k] / 10
                                    }
                                }

                                def v = new EuclidianDistance(real_src, real_tgt, 3);

                                for (int i=0; i<nb_frames; i++)
                                {
                                    writer.write(line + "\t" + (start + i) + "\t" + elts[2]  + "\t" + channels[(j/3).intValue()] + "\t" + v.distancePerFrame(i, i) + "\n")
                                }
                            }

                        }
                    }
                }
            }
        }

        project.task("generateJSONFormattedEucDistEmaPerPhoneme") {
            def input_f = project.computeEucDistEMAPerPhoneme.output_f
            ext.output_f = new File("${project.configurationEMA.output_dir}/dist_euc_ema_per_phoneme.json")
            outputs.files ext.output_f

            // Check if weights are activated
            def weights_activated = true
            def dim_weights = project.configurationEMA.weight_dim
            if (dim_weights == null)
            {
                weights_activated = false
            }

            doLast {
                def prev_utt = ""
                double[][] src;
                double[][] tgt;
                double[][] src_weight;
                double[][] tgt_weight;

                // Building map
                def val = [:]
                input_f.eachLine { line, count ->
                    if (!line.startsWith("#")) {
                        def elts = line.split()
                        if (!(elts[0] in val)) {
                            val[elts[0]] = []
                        }


                        if (elts[0] != prev_utt) {
                            //
                            src = project.configurationEMA.loading.loadFloatBinary("${nat_dir}/ema/${elts[0]}.ema", channels.size()*3); // FIXME: hardcoded frame size
                            tgt = project.configurationEMA.loading.loadFloatBinary("${synth_dir}/imposed_dur/${elts[0]}.ema", channels.size()*3); // FIXME: hardcoded frame size

                            prev_utt = elts[0]
                            if (weights_activated)
                            {
                                src_weight = project.configurationEMA.loading.loadFloatBinary("$nat_dir/weight/${elts[0]}.weight", dim_weights);
                                tgt_weight = project.configurationEMA.loading.loadFloatBinary("$synth_dir/imposed_dur/${elts[0]}.weight", dim_weights);
                            }
                        }

                        // We assume that we sequentially add everything
                        def id_frame = Integer.parseInt(elts[1])
                        if (id_frame >= val[elts[0]].size()) {
                            val[elts[0]] << [:]
                            val[elts[0]][id_frame]["coils"] = [:]
                        }

                        val[elts[0]][id_frame]["phone"] = elts[2]
                        val[elts[0]][id_frame]["phone_class"] = map_class_phone[elts[2]]
                        val[elts[0]][id_frame]["coils"][elts[3]] = [:]
                        ["x", "y", "z"].eachWithIndex { pos, idx ->
                            val[elts[0]][id_frame]["coils"][elts[3]][pos] = [:]
                            val[elts[0]][id_frame]["coils"][elts[3]][pos]["natural"] = src[id_frame][channels.indexOf(elts[3])*3+idx]
                            val[elts[0]][id_frame]["coils"][elts[3]][pos][eval_name] = tgt[id_frame][channels.indexOf(elts[3])*3+idx]

                        }

                        if (weights_activated) {
                            val[elts[0]][id_frame]["phonemeWeights"] = [:]
                            val[elts[0]][id_frame]["phonemeWeights"]["natural"] = src_weight[id_frame]
                            val[elts[0]][id_frame]["phonemeWeights"][eval_name] = tgt_weight[id_frame]
                        }

                        val[elts[0]][id_frame]["coils"][elts[3]]["distances"] = [:]
                        val[elts[0]][id_frame]["coils"][elts[3]]["distances"]["euclidian"] = Float.parseFloat(elts[4])
                    }
                }

                def output_list = []
                val.each {utt, frames ->
                    frames.eachWithIndex {frame, id_frame ->
                        def tmp = ["utterance":utt, "frame":id_frame, "time":id_frame*frameshift] + frame // FIXME: frameshift should be a parameter
                        output_list << tmp
                    }
                }

                def json = groovy.json.JsonOutput.toJson(output_list)
                output_f.text = JsonOutput.prettyPrint(json.toString())
            }
        }

        project.task("JSON2RDS") {
            dependsOn "generateJSONFormattedEucDistEmaPerPhoneme"
            jsonFile = project.generateJSONFormattedEucDistEmaPerPhoneme.output_f
            ext.output_f = new File("${project.configurationEMA.output_dir}/dist_euc_ema_per_phoneme.rds")
            outputs.files ext.output_f
            doLast {

                // Generate script
                def script_file = File.createTempFile("json2rds", ".R");
                this.getClass().getResource( 'json2rds.R' ).withInputStream { ris ->
                    script_file.withOutputStream { fos ->
                        fos << ris
                    }
                }

                // Now execute conversion
                project.exec {
                    commandLine 'Rscript', scriptFile, "--input=$jsonFile", "--output=${ext.output_f}"
                }
            }
        }
    }
}