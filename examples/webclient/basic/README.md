# WebClient example setup

1. Set port which will be used using one of these two ways.
    * Set explicit port to application.yaml in section server.port
    * Pass server port as the main method parameter to ClientMain
2. Start WebServer by calling ServerMain.main()
3. Start WebClientExample by calling ClientMain.main()
    * If you didn't set port via config file, pass generated server port to the main method as parameter