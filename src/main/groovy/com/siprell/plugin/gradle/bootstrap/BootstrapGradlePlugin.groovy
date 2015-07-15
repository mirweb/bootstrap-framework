package com.siprell.plugin.gradle.bootstrap

import org.gradle.api.file.FileTree
import org.gradle.api.Plugin
import org.gradle.api.Project

class BootstrapGradlePlugin implements Plugin<Project> {
	final BOOTSTRAP_DEFAULT_VERSION = "3.3.5"
	final FA_DEFAULT_VERSION = "4.3.0"

	void apply(Project project) {

		// Shared properties
		def downloadZipFile = new DownloadZipFile()
		String tmpDir = "${project.buildDir}/tmp"
		def properties = project.hasProperty("bootstrapFramework") ? project.bootstrapFramework : [:]
		
		// Bootstrap Framework properties
		String bootstrapVersion = properties.version ?: BOOTSTRAP_DEFAULT_VERSION
		boolean useIndividualJs = properties.useIndividualJs ?: false
		boolean useLess = properties.useLess ?: false
		String jsPath = properties.jsPath ? properties.jsPath : "grails-app/assets/javascripts"
		String cssPath = properties.cssPath ? properties.cssPath : "grails-app/assets/stylesheets"
		boolean useAssetPipeline = jsPath.contains("assets")
		
		FileTree bootstrapZipTree
		// FontAwesome properties
		def fontAwesome = properties.fontAwesome
		boolean useFontAwesome = properties.fontAwesome ? true : false
		String fontAwesomeVersion = fontAwesome?.version ?: FA_DEFAULT_VERSION
		boolean fontAwesomeUseLess = fontAwesome?.useLess ?: false
		FileTree fontAwesomeZipTree

		project.afterEvaluate {
			project.tasks.processResources.dependsOn("createFontAwesomeLess")
		}

		project.task("bootstrapFrameworkVersions") << {
			println "$BOOTSTRAP_DEFAULT_VERSION is the default Bootstrap Framework version."
			println "$FA_DEFAULT_VERSION is the default FontAwesome version."
		}

		project.task("downloadBootstrapZip") {
			String description = "Bootstrap Framework"
			String filePrefix = "bootstrap-v"
			String url = "https://github.com/twbs/bootstrap/archive/v${bootstrapVersion}.zip"
			String zipFilename = "${filePrefix}${bootstrapVersion}.zip"

			def zipFile = downloadZipFile.download(tmpDir, description, filePrefix, url, bootstrapVersion, zipFilename)
			bootstrapZipTree = (zipFile instanceof File) ? project.zipTree(zipFile) : null
		}

		project.task("downloadFontAwesomeZip", dependsOn: project.tasks.downloadBootstrapZip) {
		    if (useFontAwesome) {
    			String description = "FontAwesome"
	    		String filePrefix = "fontAwesome-v"
	    		String url = "http://fontawesome.io/assets/font-awesome-${fontAwesomeVersion}.zip"
	    		String zipFilename = "${filePrefix}${fontAwesomeVersion}.zip"
		        
    			def zipFile = downloadZipFile.download(tmpDir, description, filePrefix, url, fontAwesomeVersion, zipFilename)
    			fontAwesomeZipTree = (zipFile instanceof File) ? project.zipTree(zipFile) : null
		    }
		}

		project.task("createBootstrapJsAll", dependsOn: project.tasks.downloadFontAwesomeZip) {
			def path = "${project.projectDir}/$jsPath"
			def file = "bootstrap-all.js"
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file file
				outputs.dir path
			}
			doLast {
				if (useAssetPipeline) {
					def bootstrapJs = project.file("$path/$file")
					bootstrapJs.text = """//
// Do not edit this file. It will be overwritten by the bootstrap-framework-gradle plugin.
//
//= require bootstrap/bootstrap.js
"""
				}
			}
		}

		project.task("createBootstrapJs", dependsOn: project.tasks.createBootstrapJsAll) {
			def path = "${project.projectDir}/$jsPath/bootstrap"
			if (!project.file(path).exists()) {
				project.mkdir(path)
			}
			def files = bootstrapZipTree.matching {
				include "*/dist/js/bootstrap.js"
				if (useIndividualJs) {
					include "*/js/*.js"
				}
			}.collect()
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file files
				outputs.dir path
			}
			doLast {
				project.copy {
					from files
					into path
				}
				if (!useIndividualJs) {
					project.file(path).listFiles().each { file ->
						if (file.name != "bootstrap.js") {
							file.delete()
						}
					}
				}
			}
		}

		project.task("createBootstrapCssAll", dependsOn: project.tasks.createBootstrapJs) {
			def path = "${project.projectDir}/$cssPath"
			def file = "bootstrap-all.css"
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file file
				outputs.dir path
			}
			doLast {
				if (useAssetPipeline) {
					def bootstrapCss = project.file("$path/$file")
					bootstrapCss.text = """/*
* Do not edit this file. It will be overwritten by the bootstrap-framework-gradle plugin.
*
*= require bootstrap/css/bootstrap.css
*= require bootstrap/css/bootstrap-theme.css
*/
"""
				}
			}
		}

		project.task("createBootstrapFonts", dependsOn: project.tasks.createBootstrapCssAll) {
			def path = "${project.projectDir}/$cssPath/bootstrap/fonts"
			if (!project.file(path).exists()) {
				project.mkdir(path)
			}
			def files = bootstrapZipTree.matching {
				include "*/fonts/*"
			}.collect()
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file files
				outputs.dir path
			}
			doLast {
				project.copy {
					from files
					into path
				}
			}
		}

		project.task("createBootstrapCssIndividual", dependsOn: project.tasks.createBootstrapFonts) {
			def path = "${project.projectDir}/$cssPath/bootstrap/css"
			if (!project.file(path).exists()) {
				project.mkdir(path)
			}
			def files = bootstrapZipTree.matching {
				include "*/dist/css/*.css"
				exclude "*/dist/css/*.min.css"
			}.collect()
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file files
				outputs.dir path
			}
			doLast {
				project.copy {
					from files
					into path
				}
			}
		}

		project.task("createBootstrapLessAll", dependsOn: project.tasks.createBootstrapCssIndividual) {
			def path = "${project.projectDir}/$cssPath"
			def file = "bootstrap-less.less"
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file file
				outputs.dir path
			}
			doLast {
				if (useLess && useAssetPipeline) {
					def bootstrapLess = project.file("$path/$file")
					bootstrapLess.text = """/*
* This file is for your Bootstrap Framework less and mixin customizations.
* It was created by the bootstrap-framework-gradle plugin.
* It will not be overwritten.
*
* You can import all less and mixin files as shown below,
* or you can import them individually.
* See https://github.com/kensiprell/bootstrap-framework-gradle/blob/master/README.md#less
*/

@import "bootstrap/less/bootstrap.less";

/*
* Your customizations go below this section.
*/
"""
				}
			}
		}

		project.task("createBootstrapLess", dependsOn: project.tasks.createBootstrapLessAll) {
			def path = "${project.projectDir}/$cssPath/bootstrap/less"
			def files = bootstrapZipTree.matching {
				include "*/less/*.less"
			}.collect()
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file files
				outputs.dir path
			}
			doLast {
				if (useLess) {
					project.copy {
						from files
						into path
					}
				} else {
					project.file(path).deleteDir()
				}
			}
		}

		project.task("createBootstrapMixins", dependsOn: project.tasks.createBootstrapLess) {
			def path = "${project.projectDir}/$cssPath/bootstrap/less/mixins"
			if (useLess && !project.file(path).exists()) {
				project.mkdir(path)
			}
			def files = bootstrapZipTree.matching {
				include "*/less/mixins/*.less"
			}.collect()
			project.gradle.taskGraph.whenReady { graph ->
				inputs.file files
				outputs.dir path
			}
			doLast {
				if (useLess) {
					project.copy {
						from files
						into path
					}
				} else {
					project.file(path).deleteDir()
				}
			}
		}
		
		project.task("createFontAwesomeCssAll", dependsOn: project.tasks.createBootstrapMixins) {
		    if (useFontAwesome) {
		        def path = "${project.projectDir}/$cssPath"
			    def file = "font-awesome-all.css"
			    project.gradle.taskGraph.whenReady { graph ->
				    inputs.file file
				    outputs.dir path
			    }
		    } else {
		        def faDir = project.file("${project.projectDir}/$cssPath/font-awesome")
		        if (faDir.exists()) {
		            faDir.delete()
		        }
		    }
			doLast {
				if (useFontAwesome && useAssetPipeline) {
					def fontAwesomeCss = project.file("$path/$file")
					fontAwesomeCss.text = """/*
* Font Awesome by Dave Gandy - http://fontawesome.io
*
* Do not edit this file. It will be overwritten by the bootstrap-framework-gradle plugin.
*
*= require font-awesome/css/font-awesome.css
*= require_tree font-awesome/fonts
*/
"""
				}
			}
		}

		project.task("createFontAwesomeCssIndividual", dependsOn: project.tasks.createFontAwesomeCssAll) {
			if (useFontAwesome) {
			    def path = "${project.projectDir}/$cssPath/font-awesome/css"
		    	if (!project.file(path).exists()) {
		    		project.mkdir(path)
		    	}
		    	def files = fontAwesomeZipTree.matching {
	    			include "*/css/font-awesome.css"
		    	}.collect()
	    		project.gradle.taskGraph.whenReady { graph ->
		    		inputs.file files
		    		outputs.dir path
	        	}
			}
			doLast {
				if (useFontAwesome) {
    				project.copy {
	    				from files
	    				into path
	    			}
				}
			}
		}
		
		project.task("createFontAwesomeFonts", dependsOn: project.tasks.createFontAwesomeCssIndividual) {
			if (useFontAwesome) {
		    	def path = "${project.projectDir}/$cssPath/font-awesome/fonts"
		    	if (!project.file(path).exists()) {
		    		project.mkdir(path)
			    }
			    def files = fontAwesomeZipTree.matching {
				    include "*/fonts/*"
		    	}.collect()
		    	project.gradle.taskGraph.whenReady { graph ->
			    	inputs.file files
				    outputs.dir path
			    }
			}
			doLast {
				if (useFontAwesome) {
    				project.copy {
	    				from files
	    				into path
	    			}
				}
			}
		}

		project.task("createFontAwesomeLessAll", dependsOn: project.tasks.createFontAwesomeFonts) {
			if (useFontAwesome) {
			    def path = "${project.projectDir}/$cssPath"
			    def file = "font-awesome-less.less"
			    project.gradle.taskGraph.whenReady { graph ->
				    inputs.file file
				    outputs.dir path
		    	}
			}
			doLast {
				if (fontAwesomeUseLess) {
					def fontAwesomeLess = project.file("$path/$file")
					fontAwesomeLess.text = """/*
* Font Awesome by Dave Gandy - http://fontawesome.io
*
* This file is for your FontAwesome less and mixin customizations.
* It was created by the bootstrap-framework-gradle plugin.
* It will not be overwritten.
*
* You can import all less and mixin files as shown below,
* or you can import them individually.
* See https://github.com/kensiprell/bootstrap-framework-gradle/blob/master/README.md#font-awesome-less
*/

@import "font-awesome/less/font-awesome.less";

/*
* Your customizations go below this section.
*/
"""
				}
			}
		}

		project.task("createFontAwesomeLess", dependsOn: project.tasks.createFontAwesomeLessAll) {
			if (useFontAwesome) {
			    def path = "${project.projectDir}/$cssPath/font-awesome/less"
			    def files = fontAwesomeZipTree.matching {
				    include "*/less/*.less"
			    }.collect()
			    project.gradle.taskGraph.whenReady { graph ->
				    inputs.file files
				    outputs.dir path
			    }
			}
			doLast {
				if (fontAwesomeUseLess) {
					project.copy {
						from files
						into path
					}
				} else {
					project.file(path).deleteDir()
				}
			}
		}

	}
}

class DownloadZipFile { 
	String fileSuffix = ".zip"

	def download(String tmp, String description, String filePrefix, String url, String version, String zipFilename) {
		def tmpDir = new File("$tmp")
		if (!tmpDir.exists()) {
			tmpDir.mkdir()
		}
    	def zipFile = new File("$tmp/$zipFilename")
    	if (zipFile.exists()) {
    	    return zipFile
    	}
		try {
		    def file = zipFile.newOutputStream()
			file  << new URL(url).openStream()
			file.close()
			return zipFile
		} catch (e) {
		    zipFile.delete()
			println "Error: Could not download $url.\n$version is an invalid $description version, or you are not connected to the Internet."
			List<File> zipFiles = []
			tmpDir.listFiles().each {
				if (it.name.startsWith(filePrefix)) {
					zipFiles << it
				}
			}
			if (zipFiles.size() > 0) {
				File zipFileOld
				if (zipFiles.size() == 1) {
					zipFileOld = zipFiles[0]
				} else {
					zipFileOld = zipFiles.sort(false) { a, b ->
						def tokens = [a.name.minus(filePrefix).minus(fileSuffix), b.name.minus(filePrefix).minus(fileSuffix)]
						tokens*.tokenize('.')*.collect { it as int }.with { u, v ->
							[u, v].transpose().findResult { x, y -> x <=> y ?: null } ?: u.size() <=> v.size()
						}
					}[-1]
				}
				String newVersion = zipFileOld.name.minus(filePrefix).minus(fileSuffix)
				println "Using $description version $newVersion instead of $version."
				return zipFileOld
			} else {
			    // TODO stop tasks execution?
				println "FATAL ERROR: No old $description zip files found in $tmpDir."
			}
		}
	}
}

