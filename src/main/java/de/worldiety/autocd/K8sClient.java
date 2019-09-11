package de.worldiety.autocd;

import de.worldiety.autocd.persistence.AutoCD;
import de.worldiety.autocd.util.Environment;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentSpec;
import io.kubernetes.client.models.V1ContainerBuilder;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1LabelSelector;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1beta1HTTPIngressPathBuilder;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValueBuilder;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackendBuilder;
import io.kubernetes.client.models.V1beta1IngressRuleBuilder;
import io.kubernetes.client.models.V1beta1IngressSpecBuilder;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sClient {
    //TODO: Wait for namespace to be deleted if it still contains pods
    private static final Logger log = LoggerFactory.getLogger(K8sClient.class);
    private final CoreV1Api api;

    @Contract(pure = true)
    public K8sClient(CoreV1Api api) {
        this.api = api;
    }

    public void deployToK8s(AutoCD autoCD) throws ApiException {
        deploy(autoCD);
    }

    private void deleteNamespace(@NotNull V1Namespace namespace) {
        AppsV1Api apiInstance = new AppsV1Api();
        try {
            api.deleteNamespace(namespace.getMetadata().getName(), "true", null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Could not delete namespace", e);
        } catch (com.google.gson.JsonSyntaxException e) {
            log.info("google why");
        }
    }

    private void deleteDeployment(@NotNull ExtensionsV1beta1Deployment deployment) {
        var extensionsV1beta1Api = new ExtensionsV1beta1Api();
        extensionsV1beta1Api.setApiClient(api.getApiClient());
        try {
            extensionsV1beta1Api.deleteNamespacedDeployment(deployment.getMetadata().getName(), deployment.getMetadata().getNamespace(), "true", null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Could not delete deployment", e);
        } catch (com.google.gson.JsonSyntaxException e) {
            log.info("google why");
        }
    }

    private void deleteService(@NotNull V1Service service) {
        try {
            api.deleteNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace(), null, null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Could not delete service", e);
        } catch (com.google.gson.JsonSyntaxException e) {
            log.info("google why");
        }
    }

    private void deleteIngress(@NotNull V1beta1Ingress ingress) {
        var extensionsV1beta1Api = new ExtensionsV1beta1Api();
        extensionsV1beta1Api.setApiClient(api.getApiClient());
        try {
            extensionsV1beta1Api.deleteNamespacedIngress(ingress.getMetadata().getName(), ingress.getMetadata().getNamespace(), null, null, null, null, null, null);
        } catch (ApiException e) {
            log.error("Could not delete ingress", e);
        } catch (com.google.gson.JsonSyntaxException e) {
            log.info("google why");
        }
    }

    private void deploy(AutoCD autoCD) throws ApiException {
        var ingress = getIngress(autoCD);
        deleteIngress(ingress);
        var service = getService(autoCD);
        deleteService(service);
        var deployment = getDeployment(autoCD);
        deleteDeployment(deployment);
        var nameSpace = getNamespace();
        deleteNamespace(nameSpace);

        AppsV1Api apiInstance = new AppsV1Api();
        var extensionsV1beta1Api = new ExtensionsV1beta1Api();
        var appsV1Api = new AppsV1Api();
        extensionsV1beta1Api.setApiClient(api.getApiClient());
        appsV1Api.setApiClient(api.getApiClient());
        apiInstance.setApiClient(api.getApiClient());
        api.createNamespace(nameSpace, false, "true", null);
        extensionsV1beta1Api.createNamespacedDeployment(deployment.getMetadata().getNamespace(), deployment, false, "true", null);
        api.createNamespacedService(service.getMetadata().getNamespace(), service, false, "true", null);
        extensionsV1beta1Api.createNamespacedIngress(ingress.getMetadata().getNamespace(), ingress, false, "true", null);
    }

    @NotNull
    private V1beta1Ingress getIngress(@NotNull AutoCD autoCD) {
        var ingress = new V1beta1Ingress();
        ingress.setKind("Ingress");
        var meta = getNamespacedMeta();
        meta.setName(getNamespaceString() + "-ingress");

        var spec = new V1beta1IngressSpecBuilder()
                .withRules(new V1beta1IngressRuleBuilder()
                        .withHost(autoCD.getSubdomain())
                        .withHttp(new V1beta1HTTPIngressRuleValueBuilder()
                                .withPaths(new V1beta1HTTPIngressPathBuilder().withPath("/")
                                        .withBackend(new V1beta1IngressBackendBuilder()
                                                .withServiceName(getNamespaceString() + "-service")
                                                .withServicePort(new IntOrString(autoCD.getServicePort()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        ingress.setSpec(spec);
        ingress.setMetadata(meta);

        return ingress;
    }

    @NotNull
    private V1Service getService(@NotNull AutoCD autoCD) {
        var service = new V1Service();
        service.setKind("Service");
        var meta = getNamespacedMeta();
        meta.setName(getNamespaceString() + "-service");

        var spec = new V1ServiceSpec();
        spec.setSelector(Map.of("k8s-app", getNamespaceString()));
        var port = new V1ServicePort();
        port.setName("web");
        port.setPort(80);
        port.setTargetPort(new IntOrString(autoCD.getContainerPort()));
        spec.setPorts(List.of(port));

        service.setSpec(spec);
        service.setMetadata(meta);

        return service;
    }

    @NotNull
    private ExtensionsV1beta1Deployment getDeployment(@NotNull AutoCD autoCD) {
        var meta = getNamespacedMeta();
        meta.setName(getNamespaceString());
        var labels = Map.of("k8s-app", getNamespaceString());
        meta.setLabels(labels);

        var spec = new ExtensionsV1beta1DeploymentSpec();
        spec.setReplicas(1);
        var select = new V1LabelSelector();
        select.setMatchLabels(labels);
        spec.setSelector(select);

        var template = new V1PodTemplateSpec();
        spec.setTemplate(template);
        var templateMeta = new V1ObjectMeta();
        template.setMetadata(templateMeta);

        templateMeta.setLabels(Map.of(
                "k8s-app", getNamespaceString(),
                "name", getName()));
        template.setMetadata(templateMeta);

        var podSpec = new V1PodSpec();
        template.setSpec(podSpec);

        podSpec.setTerminationGracePeriodSeconds(60L);
        var port = new V1ContainerPort();
        port.setContainerPort(8080);
        port.setName("http");

        var container = new V1ContainerBuilder()
                .withImage(autoCD.getRegistryImagePath())
                .withName(getName() + "-c")
                .withPorts(port)
                .build();

        podSpec.setContainers(List.of(container));

        var secret = new V1LocalObjectReference();
        secret.setName("gitlab-bot");
        podSpec.setImagePullSecrets(List.of(secret));

        var dep = new ExtensionsV1beta1Deployment();
        dep.setMetadata(meta);
        dep.setSpec(spec);
        dep.setKind("Deployment");
        dep.setApiVersion(getApiVersion());
        return dep;
    }

    @NotNull
    @Contract(pure = true)
    private String getApiVersion() {
        return "extensions/v1beta1";
    }

    private String getName() {
        if (isLocal()) {
            return "local-default-name";
        }

        return System.getenv(Environment.CI_PROJECT_NAME.toString());
    }

    private boolean isLocal() {
        var reg = System.getenv(Environment.CI_REGISTRY.toString());
        return reg == null;
    }

    @NotNull
    private V1ObjectMeta getNamespacedMeta() {
        var metadata = new V1ObjectMeta();
        metadata.setNamespace(getNamespaceString());
        return metadata;
    }

    private String getNamespaceString() {
        var nameSpaceName = "local-default";

        if (!isLocal()) {
            nameSpaceName = System.getenv(Environment.CI_PROJECT_NAMESPACE.toString());
        }

        return nameSpaceName;
    }

    @NotNull
    private V1Namespace getNamespace() {
        var ns = new V1Namespace();
        var metadata = new V1ObjectMeta();
        metadata.setName(getNamespaceString());

        ns.setMetadata(metadata);
        return ns;
    }
}
