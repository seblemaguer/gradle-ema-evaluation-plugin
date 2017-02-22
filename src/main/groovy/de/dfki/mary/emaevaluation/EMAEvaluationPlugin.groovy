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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

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


        // Load configuration
        def slurper = new JsonSlurper()
        def config_file = project.rootProject.ext.config_file
        def config = slurper.parseText( config_file.text )

        // Adapt pathes
        DataFileFinder.project_path = new File(getClass().protectionDomain.codeSource.location.path).parent
        if (config.data.project_dir) {
            DataFileFinder.project_path = config.data.project_dir
        }

        // See for number of processes for parallel mode
        def nb_proc_local = 1
        if (project.gradle.startParameter.getMaxWorkerCount() != 0) {
            nb_proc_local = Runtime.getRuntime().availableProcessors(); // By default the number of core
            if (config.settings.nb_proc) {
                if (config.settings.nb_proc > nb_proc_local) {
                    throw Exception("You will overload your machine, preventing stop !")
                }

                nb_proc_local = config.settings.nb_proc
            }
        }

        (new File(project.rootProject.buildDir.toString() + "/EMAAnalysis")).mkdirs()

        project.ext {
            // User configuration
            user_configuration = config;

            emaOutputDir = new File(project.rootProject.buildDir.toString() + "/EMAAnalysis")

            // FIXME: externalize that !
            list_file = new File(DataFileFinder.getFilePath("list_test"))
            referenceDir = ["ema": "../extraction/build/ema"]
            synthesizeDir = ["ema": "../synthesis/build/output/imposed_dur"]
            channels = ["T3", "T2", "T1", "ref", "jaw", "upperlip", "lowerlip"]

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
            (new EMAAnalysis()).addTasks(project)


            project.task("generateEMAReport") {
                dependsOn "computeRMSEEMA", "computeEucDistEMA"


                def input_rms_ema = new File("${project.emaOutputDir}/rms_ema.csv")

                def ema_input_file = []
                project.channels.each { c ->
                    ema_input_file << new File("${project.emaOutputDir}/euc_dist_${c}.csv")
                }

                def output_f = new File("${project.emaOutputDir}/global_report.csv")
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