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

        (new File(project.rootProject.buildDir.toString() + "/EMAAnalysis")).mkdirs()

        project.ext {
            // User configuration
            user_configuration = config;

            emaOutputDir = new File(project.rootProject.buildDir.toString() + "/EMAAnalysis")

            // FIXME: externalize that !
            list_file = new File(DataFileFinder.getFilePath("list_test"))
            channels =

            nb_proc = nb_proc_local;

            loading = new LoadingHelpers();
        }

        project.status = project.version.endsWith('SNAPSHOT') ? 'integration' : 'release'

        project.repositories {
            jcenter()
            maven {
                url 'http://oss.jfrog.org/artifactory/repo'
            }
        }

        project.configurations.create 'legacy'

        project.sourceSets {
            main {
                java {
                    // srcDir project.generatedSrcDir
                }
            }
            test {
                java {
                    // srcDir project.generatedTestSrcDir
                }
            }
        }

        project.afterEvaluate {

            project.task("configurationEMA")  {
                dependsOn "configuration"

                // Input
                ext.list_basenames = project.configuration.list_basenames ? project.configuration.list_basenames : []
                ext.reference_dir = project.configuration.reference_dir ? project.configuration.reference_dir:[]
                ext.synthesize_dir = project.configuration.synthesize_dir ? project.configuration.synthesize_dir:[]


                // Some parameters
                ext.channels = project.configuration.channels ? project.configuration.channels : []
                ext.nb_proc = project.configuration.nb_proc ? project.configuration.nb_proc : 1

                // Outputdir
                ext.output_dir = new File(project.rootProject.buildDir.toString() + "/EMAAnalysis");

                // Loading helping
                ext.loading = new LoadingHelpers();
            }
            (new EMAAnalysis()).addTasks(project)


            project.task("generateEMAReport") {
                dependsOn "configurationEMA", "computeRMSEEMA", "computeEucDistEMA"


                def input_rms_ema = project.computeRMSEEMA.output_f
                def ema_input_file = []
                project.channels.each { c ->
                    ema_input_file << new File("${project.configurationEMA.output_dir}/euc_dist_${c}.csv")
                }

                def output_f = new File("${project.configurationEMA.output_dir}/global_report.csv")
                outputs.files output_f

                doLast {
                    def values = null
                    def dist = null
                    def s = null
                    output_f.text = "#id\tmean\tstd\tconfint\n"

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
                    output_f << "rms ema\t" << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\n"

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
                        output_f << "euc dist ${project.channels[i]} (cm)\t" << s.mean() << "\t" << s.stddev() << "\t" << s.confint(0.05) << "\n"
                    }
                }
            }
        }
    }
}