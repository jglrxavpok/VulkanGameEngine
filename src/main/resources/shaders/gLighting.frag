#version 450
#extension GL_ARB_separate_shader_objects : enable
layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput gColor;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput gPos;
layout(input_attachment_index = 2, binding = 2) uniform subpassInput gNormal;
#include "lighting.glsl.h"
layout(input_attachment_index = 3, binding = 4) uniform subpassInput gSpecular;

layout(location = 0) out vec4 outColor;

float exposure(vec3 fragNormal, vec3 lightDirection) {
    return max(dot(-fragNormal, lightDirection), 0.0);
}

vec3 computeSpecular(vec3 reflectedColor, float specularIntensity, vec3 fragToEye, vec3 reflectedRay) {
    float specularPower = 32.0f; // TODO: don't harcode
    float specularFactor = dot(fragToEye, reflectedRay);
    if(specularFactor > 0.0) {
        specularFactor = pow(specularFactor, specularPower);
        vec3 specular = reflectedColor * specularIntensity * specularFactor;
        return specular;
    }
    return vec3(0.0);
}

void main() {
    vec3 fragPosition = subpassLoad(gPos).xyz;
    vec3 fragNormal = subpassLoad(gNormal).xyz;
    vec3 color = subpassLoad(gColor).rgb;
    float specularIntensity = subpassLoad(gSpecular).r;

    vec3 lighting = color * lights.ambientLight.color;
    for(int i = 0; i < MAX_POINT_LIGHTS; i++) {
        PointLight light = lights.pointLights[i];

        vec3 lightToPoint = fragPosition - light.viewPosition;
        vec3 lightDir = normalize(lightToPoint);
        float distance = length(lightToPoint);
        float attenuation = light.attenuationConstant*1+light.attenuationLinear*distance+light.attenuationQuadratic*distance*distance;
        vec3 reflectedColor = color * light.color * light.intensity;
        vec3 diffuse = exposure(fragNormal, lightDir) * reflectedColor/attenuation;
        lighting += diffuse;

        vec3 reflectedRay = normalize(reflect(lightDir, fragNormal));
        vec3 fragToEye = normalize(-fragPosition);

        lighting += computeSpecular(reflectedColor, specularIntensity, fragToEye, reflectedRay);
    }
    for(int i = 0; i < MAX_DIRECTIONAL_LIGHTS; i++) {
        DirectionalLight light = lights.directionalLights[i];

        vec3 lightDir = normalize(light.viewDirection);
        vec3 reflectedColor = color * light.color * light.intensity;
        vec3 diffuse = exposure(fragNormal, lightDir) * reflectedColor;
        lighting += diffuse;

        vec3 reflectedRay = normalize(reflect(light.viewDirection, fragNormal));
        vec3 fragToEye = normalize(-fragPosition);

        lighting += computeSpecular(reflectedColor, specularIntensity, fragToEye, reflectedRay);
    }

    for(int i = 0; i < MAX_SPOT_LIGHTS; i++) {
        SpotLight light = lights.spotLights[i];
        vec3 lightToPoint = fragPosition - light.viewPosition;
        vec3 lightDir = normalize(lightToPoint);

        float spotFactor = max(dot(lightDir, normalize(light.viewDirection)), 0.0);
        if(spotFactor < cos(light.angle/2.0f)) {
            spotFactor = 0.0f;
        } else {
            float minDot = cos(light.angle/2.0f);
            float dotRange = 1.0f-minDot;
            spotFactor = (spotFactor-minDot)/dotRange;
        }

        float distance = length(lightToPoint);
        float attenuation = light.attenuationConstant*1+light.attenuationLinear*distance+light.attenuationQuadratic*distance*distance;
        vec3 reflectedColor = color * light.color * light.intensity;
        vec3 diffuse = exposure(fragNormal, lightDir) * reflectedColor/attenuation * spotFactor;
        lighting += diffuse;

        vec3 reflectedRay = normalize(reflect(lightDir, fragNormal));
        vec3 fragToEye = normalize(-fragPosition);

        lighting += computeSpecular(reflectedColor, specularIntensity, fragToEye, reflectedRay) * spotFactor;
    }
    outColor = vec4(lighting, 1.0);
}