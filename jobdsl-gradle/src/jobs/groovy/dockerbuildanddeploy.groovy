import job.JobBuilder

import javaposse.jobdsl.dsl.DslFactory

// This is a tmp hack. We should replace this job with pipeline
// since pipeline jobs have better support for reading credentials id
// and slave to run on from env variables
def default_credentials = Helpers.readEnvVariable("default_credentials", "jenkins")
def utilitySlave = Helpers.readEnvVariable("utility_slave", "utility-slave")
def registry_url = Helpers.readEnvVariable("registry_url", "default_registry_url")
def registry_credentials = Helpers.readEnvVariable("registry_credentials", "default_registry_credId")

new JobBuilder(this as DslFactory, "jenkins_as_a_code-build-and-deploy-jenkins")
        .addLogRotator()
        .addLabel(utilitySlave)
        .addScmBlock('$default_repo', '$default_branch', default_credentials)
        .addScmPollTrigger("@midnight")
        .addGradleStep(["buildXMl"], "jobdsl-gradle")
        .addGradleStep(["test"], "jobdsl-gradle")
        //Check first! .addShellStep("TAG=\$(git describe --tags); echo TAG=\$TAG > tmp.properties")
        .addInjectedEnvVariable([TAG:'$(git describe --tags)'], 'tmp.properties')
        .addDockerBuildAndPublish('${master_image_name}',
                                  '${TAG}',
                                  registry_url,
                                  registry_credentials,
                                  "./dockerizeit/master",
                                  "--build-arg master_image_version=\${master_image_name}:\${TAG} --build-arg http_proxy --build-arg https_proxy --build-arg no_proxy --build-arg JAVA_OPTS",
                                  './dockerizeit/master/Dockerfile')
        .addDockerBuildAndPublish('${slave_image_name}',
                                  '${TAG}',
                                  registry_url,
                                  registry_credentials,
                                  "./dockerizeit/slave",
                                  "--build-arg http_proxy --build-arg https_proxy --build-arg no_proxy --build-arg JAVA_OPTS",
                                  './dockerizeit/slave/Dockerfile')
        .addShellStep("./dockerizeit/generate-compose.py --debug --file dockerizeit/docker-compose.yml --jmaster-image \${master_image_name} --jmaster-version \$(git describe --tags) --jslave-image \${slave_image_name} --jslave-version \$(git describe --tags)")
        .addShellStep("cp docker-compose.yml dockerizeit/munchausen/")
        .addShellStep("cd dockerizeit/munchausen && docker build --build-arg http_proxy --build-arg https_proxy --build-arg no_proxy -t munchausen . && docker run -d -v /var/run/docker.sock:/var/run/docker.sock munchausen \$(git describe --tags)")
        .addArchiveArtifacts(['docker-compose.yml'])
        .build()



