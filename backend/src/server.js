const { createCerevixBackend } = require("./app");

const { server, config, readyPromise } = createCerevixBackend();

readyPromise
  .then(() => {
    server.listen(config.port, "0.0.0.0", () => {
      console.log(
        JSON.stringify({
          level: "info",
          message: `CEREVIX AI Backend listening on port ${config.port}`,
          env: config.nodeEnv,
          projectId: config.firebaseProjectId,
        })
      );
    });
  })
  .catch((err) => {
    console.error("Failed to initialize CEREVIX AI backend:", err);
    process.exit(1);
  });
