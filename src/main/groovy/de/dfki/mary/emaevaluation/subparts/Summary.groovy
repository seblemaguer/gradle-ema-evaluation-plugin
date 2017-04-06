package de.dfki.mary.emaevaluation.subparts

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import org.gradle.api.Project
import java.util.regex.*
import de.dfki.mary.ttsanalysis.AnalysisInterface

import marytts.analysis.distances.acoustic.*;
import marytts.analysis.distances.graphic.*;
import marytts.analysis.alignment.IDAlignment;

class EMASummaryAnalysis implements AnalysisInterface
{
    public void addTasks(Project project)
    {
        project.task("generateJSONFormattedEucDistEmaPerPhoneme") {
            dependsOn "computeEucDistEMAPerPhoneme", "computeEucDistEMADynPerPhoneme"
            def input_f_static = project.computeEucDistEMAPerPhoneme.output_f
            def input_f_dyn = project.computeEucDistEMADynPerPhoneme.output_f
            inputs.files input_f_static, input_f_dyn

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
                def dyn_lines = input_f_dyn.readLines()

                input_f_static.eachLine { line, id_line ->

                    if (!line.startsWith("#")) {
                        def elts = line.split()
                        if (!(elts[0] in val)) {
                            val[elts[0]] = []
                        }
                        def elts_dyn = dyn_lines[id_line-1].split()


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
                            val[elts[0]][id_frame]["coils"][elts[3]][pos]["natural"] = src[id_frame][project.configurationEMA.channel_labels.indexOf(elts[3])*3+idx]
                            val[elts[0]][id_frame]["coils"][elts[3]][pos][project.configurationEMA.id_expe] = tgt[id_frame][project.configurationEMA.channel_labels.indexOf(elts[3])*3+idx]

                        }

                        if (weights_activated) {
                            val[elts[0]][id_frame]["phonemeWeights"] = [:]
                            val[elts[0]][id_frame]["phonemeWeights"]["natural"] = src_weight[id_frame]
                            val[elts[0]][id_frame]["phonemeWeights"][project.configurationEMA.id_expe] = tgt_weight[id_frame]
                        }

                        // FIXME: not really nice
                        val[elts[0]][id_frame]["coils"][elts[3]]["euc_static"] = [:]
                        val[elts[0]][id_frame]["coils"][elts[3]]["euc_static"]["euc_static"] = Float.parseFloat(elts[4])
                        val[elts[0]][id_frame]["coils"][elts[3]]["euc_dyn"] = [:]
                        val[elts[0]][id_frame]["coils"][elts[3]]["euc_dyn"]["euc_dyn"] = Float.parseFloat(elts_dyn[4])
                    }
                }

                def output_list = []
                val.each {utt, frames ->
                    frames.eachWithIndex {frame, id_frame ->
                        def tmp = ["utterance":utt, "frame":id_frame, "time":id_frame*project.configurationEMA.frameshift*0.001] + frame // FIXME: frameshift should be a parameter
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

        project.task("generateTikzExample") {
            dependsOn "JSON2RDS"
            def rds_file = project.JSON2RDS.output_f
            ext.output_f = new File("${project.configurationEMA.output_dir}/example/tikz/")
            ext.output_f.mkdirs()
            doLast {


                // Generate script
                def script_file = File.createTempFile("plot_example", ".R");
                this.getClass().getResource( 'plot_example.R' ).withInputStream { ris ->
                    script_file.withOutputStream { fos ->
                        fos << ris
                    }
                }


                // Now execute conversion
                def dummyOutputStream = new OutputStream() {
                    @Override
                    public void write(int b) {}
                }
                withPool(project.configurationEMA.nb_proc) {
                    project.configurationEMA.list_basenames.eachParallel { basename ->
                        project.exec {

                            standardOutput = dummyOutputStream
                            commandLine 'Rscript', script_file, "--input=$rds_file", "--output=${ext.output_f}/${basename}.tex",
                            "--labfilename=${project.configurationEMA.lab_dir}/${basename}.lab", "--utterance=$basename", "--width=8", "--height=5"
                        }
                    }
                }
            }
        }

        project.task("plotExample") {
            dependsOn "generateTikzExample"
            def rds_file = project.JSON2RDS.output_f
            ext.output_f = new File("${project.configurationEMA.output_dir}/example/pdf/")
            ext.output_f.mkdirs()
            doLast {

                def dummyOutputStream = new OutputStream() {
                    @Override
                    public void write(int b) {}
                }
                // Now execute conversion
                withPool(project.configurationEMA.nb_proc) {
                    project.configurationEMA.list_basenames.eachParallel { basename ->
                        project.exec {

                            standardOutput = dummyOutputStream
                            commandLine 'pdflatex', "-output-directory", "${ext.output_f}", "${project.generateTikzExample.output_f}/${basename}.tex"
                        }
                    }
                }
            }
        }
    }
}