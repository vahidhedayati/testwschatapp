package testwschatapp

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

class Application extends GrailsAutoConfiguration {
    Closure doWithSpring() {
        {->
            wsChatConfig DefaultWsChatConfig
        }
    }
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}