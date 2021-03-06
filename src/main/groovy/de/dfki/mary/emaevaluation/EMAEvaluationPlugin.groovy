package de.dfki.mary.emaevaluation


import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import marytts.analysis.utils.LoadingHelpers;
import marytts.analysis.Statistics;

import de.dfki.mary.emaevaluation.subparts.*

class EMAEvaluationPlugin implements Plugin<Project>
{
    @Override
    void apply(Project project)
    {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_7


        project.afterEvaluate {

            project.task("configurationEMA")  {
                dependsOn "configuration"

                // Because we need an id for the current expe
                ext.id_expe = project.configuration.hasProperty("id_expe") ? project.configuration.id_expe : null

                // Input
                ext.list_basenames = project.configuration.hasProperty("list_basenames") ? project.configuration.list_basenames : []
                ext.reference_dir = project.configuration.hasProperty("reference_dir") ? project.configuration.reference_dir : []
                ext.synthesize_dir = project.configuration.hasProperty("synthesize_dir") ? project.configuration.synthesize_dir : []
                ext.lab_dir = project.configuration.hasProperty("lab_dir") ? project.configuration.lab_dir : []

                // Some parameters
                ext.channels = project.configuration.hasProperty("channels") ? project.configuration.channels : []
                ext.channel_labels = project.configuration.hasProperty("channel_labels") ? project.configuration.channel_labels : []
                ext.nb_proc = project.configuration.hasProperty("nb_proc") ? project.configuration.nb_proc : 1
                ext.weight_dim = project.configuration.hasProperty("weight_dim") ? project.configuration.weight_dim : null
                ext.frameshift = project.configuration.hasProperty("frameshift") ? project.configuration.frameshift : 5

                // FIXME: generalize outside
                ext.map_phone2class = ["aa": "vowel", "ae": "vowel", "ah": "vowel", "an": "vowel", "ao": "vowel", "aw": "vowel", "ax": "vowel", "axr": "approximant", "ay": "vowel", "b": "labial", "ch": "coronal", "d": "coronal", "dh": "coronal", "eh": "vowel", "el": "coronal", "em": "labial", "en": "coronal", "er": "approximant", "ey": "vowel", "f": "labial", "g": "dorsal", "hh": "glottal", "ih": "vowel", "ix": "vowel", "iy": "vowel", "jh": "coronal", "k": "dorsal", "l": "coronal", "m": "labial", "n": "coronal", "ng": "dorsal", "oo": "vowel", "ow": "vowel", "oy": "vowel", "p": "labial", "pau": "silence", "r": "approximant", "s": "coronal", "sh": "coronal", "t": "coronal", "th": "coronal", "uh": "vowel", "ur": "approximant", "uw": "vowel", "v": "labial", "w": "labial", "y": "vowel", "z": "coronal", "zh": "coronal"]

                // Outputdir
                ext.output_dir = new File(project.rootProject.buildDir.toString() + "/output/EMAAnalysis");
                output_dir.mkdirs()

                // Loading helping
                ext.loading = new LoadingHelpers();
            }

            (new EMAAnalysis()).addTasks(project)
            (new EMAPhonemeAnalysis()).addTasks(project)
            (new EMADynamicAnalysis()).addTasks(project)
            (new EMASummaryAnalysis()).addTasks(project)


            project.task("generateEMAReport") {
                dependsOn "configurationEMA"

                // FIXME: kind of ugly for now :/
                if (!project.configurationEMA.reference_dir.containsKey("ema")) {
                    return;
                }

                // FIXME: hardcoded !
                dependsOn "computeRMSEEMA", "computeEucDistEMA", "computeEucDistEMAWithoutSilence"
                dependsOn "computeRMSEEMADyn", "computeRMSEEMADynWithoutSilence"
                dependsOn "plotExample"

                def input_rms_ema = project.computeRMSEEMA.output_f
                def ema_input_file = []
                project.configurationEMA.channel_labels.each { c ->
                    ema_input_file << new File("${project.configurationEMA.output_dir}/euc_dist_${c}.csv")
                }
                def ema_input_no_sil_file = []
                project.configurationEMA.channel_labels.each { c ->
                    ema_input_no_sil_file << new File("${project.configurationEMA.output_dir}/euc_dist_no_sil_${c}.csv")
                }

                def ema_dyn_input_file = []
                project.configurationEMA.channel_labels.each { c ->
                    ema_dyn_input_file << new File("${project.configurationEMA.output_dir}/rmse_dyn_${c}.csv")
                }
                def ema_dyn_input_no_sil_file = []
                project.configurationEMA.channel_labels.each { c ->
                    ema_dyn_input_no_sil_file << new File("${project.configurationEMA.output_dir}/rmse_dyn_no_sil_${c}.csv")
                }

                ext.output_f = new File("${project.configurationEMA.output_dir}/global_report.csv")
                outputs.files ext.output_f

                doLast {
                    def values = null
                    def dist = null
                    def s = null
                    ext.output_f.text = "#id\tmean\tstd\tconfint\tnb_values\n"

                    // RMS part
                    values = []
                    input_rms_ema.eachLine { line ->
                        if (line.startsWith("#"))
                            return; // Continue...

                            def elts = line.split()
                            values << Double.parseDouble(elts[1])
                    }
                    dist = new Double[values.size()];
                    values.toArray(dist);
                    s = new Statistics(dist);
                    ext.output_f << "rms ema (cm)" << "\t"
                    ext.output_f << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\t"
                    ext.output_f << values.size() << "\n"


                    // Euclidian distances part
                    ema_input_file.eachWithIndex { c_file, i ->

                        values = []
                        c_file.eachLine { line ->
                            if (line.startsWith("#"))
                                return; // Continue...

                                values << Double.parseDouble(line)
                        }
                        dist = new Double[values.size()];
                        values.toArray(dist);
                        s = new Statistics(dist);
                        ext.output_f << "euc dist ${project.configurationEMA.channel_labels[i]} (cm)" << "\t"
                        ext.output_f << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\t"
                        ext.output_f << values.size() << "\n"
                    }


                    // Euclidian distances part
                    ema_input_no_sil_file.eachWithIndex { c_file, i ->

                        values = []
                        c_file.eachLine { line ->
                            if (line.startsWith("#"))
                                return; // Continue...

                                values << Double.parseDouble(line)
                        }
                        dist = new Double[values.size()];
                        values.toArray(dist);
                        s = new Statistics(dist);
                        ext.output_f << "euc dist ${project.configurationEMA.channel_labels[i]} without sil (cm)" << "\t"
                        ext.output_f << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\t"
                        ext.output_f << values.size() << "\n"
                    }




                    // Euclidian distances part
                    ema_dyn_input_file.eachWithIndex { c_file, i ->

                        values = []
                        c_file.eachLine { line ->
                            if (line.startsWith("#"))
                                return; // Continue...

                                values << Double.parseDouble(line)
                        }
                        dist = new Double[values.size()];
                        values.toArray(dist);
                        s = new Statistics(dist);
                        ext.output_f << "RMSE ${project.configurationEMA.channel_labels[i]} (dyn)" << "\t"
                        ext.output_f << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\t"
                        ext.output_f << values.size() << "\n"
                    }


                    // Euclidian distances part
                    ema_dyn_input_no_sil_file.eachWithIndex { c_file, i ->

                        values = []
                        c_file.eachLine { line ->
                            if (line.startsWith("#"))
                                return; // Continue...

                                values << Double.parseDouble(line)
                        }
                        dist = new Double[values.size()];
                        values.toArray(dist);
                        s = new Statistics(dist);
                        ext.output_f << "RMSE ${project.configurationEMA.channel_labels[i]} without sil (dyn)" << "\t"
                        ext.output_f << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\t"
                        ext.output_f << values.size() << "\n"
                    }
                }
            }
        }
    }
}
