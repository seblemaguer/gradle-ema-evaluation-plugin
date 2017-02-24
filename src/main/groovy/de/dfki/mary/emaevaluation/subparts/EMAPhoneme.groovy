package de.dfki.mary.emaevaluation.subparts

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
                        (new File("${project.configurationEMA.reference_dir['dur']}/${line}.lab")).eachLine { label -> // FIXME: hardcoded reference name
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
                                        real_tgt[i][k] = tgt[i][j+k] / 10
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

        project.task("generateJSONFormattedEucDistEmaPerPhoneme") {
            dependsOn "computeEucDistEMAPerPhoneme"
            def input_f = project.computeEucDistEMAPerPhoneme.output_f
            inputs.files input_f
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
                            src = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.reference_dir['ema']}/${elts[0]}.ema", project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size
                            tgt = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.synthesize_dir['ema']}/${elts[0]}.ema", project.configurationEMA.channels.size()*3); // FIXME: hardcoded frame size

                            prev_utt = elts[0]
                            if (weights_activated)
                            {
                                src_weight = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.reference_dir['weight']}/${elts[0]}.weight", dim_weights);
                                tgt_weight = project.configurationEMA.loading.loadFloatBinary("${project.configurationEMA.synthesize_dir['weight']}/${elts[0]}.weight", dim_weights);
                            }
                        }

                        // We assume that we sequentially add everything
                        def id_frame = Integer.parseInt(elts[1])
                        if (id_frame >= val[elts[0]].size()) {
                            val[elts[0]] << [:]
                            val[elts[0]][id_frame]["coils"] = [:]
                        }

                        // Be sure e are dealing with current phoneme
                        def phone = elts[2]
                        Matcher match_phone = phone =~ /^[^-]*-([^+]*)[+].*/
                        if (match_phone.matches()) {
                            phone = match_phone.group(1)
                        }

                        val[elts[0]][id_frame]["phone"] = phone
                        val[elts[0]][id_frame]["phone_class"] = project.configurationEMA.map_phone2class[phone]
                        val[elts[0]][id_frame]["coils"][elts[3]] = [:]
                        ["x", "y", "z"].eachWithIndex { pos, idx ->
                            val[elts[0]][id_frame]["coils"][elts[3]][pos] = [:]
                            val[elts[0]][id_frame]["coils"][elts[3]][pos]["natural"] = src[id_frame][project.configurationEMA.channels.indexOf(elts[3])*3+idx]
                            val[elts[0]][id_frame]["coils"][elts[3]][pos][project.configurationEMA.id_expe] = tgt[id_frame][project.configurationEMA.channels.indexOf(elts[3])*3+idx]

                        }

                        if (weights_activated) {
                            val[elts[0]][id_frame]["phonemeWeights"] = [:]
                            val[elts[0]][id_frame]["phonemeWeights"]["natural"] = src_weight[id_frame]
                            val[elts[0]][id_frame]["phonemeWeights"][project.configurationEMA.id_expe] = tgt_weight[id_frame]
                        }

                        val[elts[0]][id_frame]["coils"][elts[3]]["distances"] = [:]
                        val[elts[0]][id_frame]["coils"][elts[3]]["distances"]["euclidian"] = Float.parseFloat(elts[4])
                    }
                }

                def output_list = []
                val.each {utt, frames ->
                    frames.eachWithIndex {frame, id_frame ->
                        def tmp = ["utterance":utt, "frame":id_frame, "time":id_frame*project.configurationEMA.frameshift] + frame // FIXME: frameshift should be a parameter
                        output_list << tmp
                    }
                }

                def json = groovy.json.JsonOutput.toJson(output_list)
                output_f.text = groovy.json.JsonOutput.prettyPrint(json.toString())
            }
        }

        project.task("JSON2RDS") {
            dependsOn "generateJSONFormattedEucDistEmaPerPhoneme"
            def jsonFile = project.generateJSONFormattedEucDistEmaPerPhoneme.output_f
            inputs.files jsonFile
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
                    commandLine 'Rscript', script_file, "--input=$jsonFile", "--output=${ext.output_f}"
                }
            }
        }
    }
}