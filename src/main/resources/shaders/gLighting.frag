#version 450
#extension GL_ARB_separate_shader_objects : enable
layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput gColor;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput gPos;
layout(input_attachment_index = 2, binding = 2) uniform subpassInput gNormal;
#include "lighting.glsl.h"
layout(input_attachment_index = 3, binding = 4) uniform subpassInput gSpecular;
#include "shadowMapping/bindings.glsl.h"

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

int adjustOffset(int baseShadowMapIndex, vec4 shadowMapPos, int lightType) {
    switch(lightType) {
        case TYPE_DIRECTIONAL:
            // TODO
            return baseShadowMapIndex;

        case TYPE_POINT:
            // TODO
            return baseShadowMapIndex;

        case TYPE_SPOT:
            return baseShadowMapIndex;

        default:
            return baseShadowMapIndex;
    }
}

float calcShadow(vec3 fragPositionInViewSpace, int shadowMapIndex, int lightType) {
    if(shadowMapIndex < 0 || shadowMapIndex >= MAX_SHADOW_MAPS) {
        return 1.0f;
    }
    vec4 lightViewPos = worldToProjectedMat.matrices[shadowMapIndex].view * lights.invertedView * vec4(fragPositionInViewSpace, 1.0);

    vec4 shadowMapPos = worldToProjectedMat.matrices[shadowMapIndex].projection * lightViewPos;
    shadowMapPos.xyz /= shadowMapPos.w;

    vec2 shadowMapTexCoords = shadowMapPos.xy * 0.5 + 0.5; // NDC to texture coords
    const float bias = 0.001f;
    float shadow = 1.0f;
    if(shadowMapPos.z > 0.0 && shadowMapPos.z < 1.0) {
        int actualIndex = adjustOffset(shadowMapIndex, shadowMapPos, lightType);
        float depth = texture(shadowMaps[shadowMapIndex], shadowMapTexCoords).r;
        if(shadowMapPos.w > 0.0 && shadowMapTexCoords.x > 0.0 && shadowMapTexCoords.x < 1.0 && shadowMapTexCoords.y > 0.0 && shadowMapTexCoords.y < 1.0) {
            if((shadowMapPos.z-depth) > bias) {
                shadow = 0.0f;
            }
        }
    }

    return shadow;
}

void main() {
    vec3 fragPosition = subpassLoad(gPos).xyz;
    vec3 fragNormal = subpassLoad(gNormal).xyz;
    vec3 color = subpassLoad(gColor).rgb;

    float specularIntensity = subpassLoad(gSpecular).r;

    vec3 lighting = color * lights.ambientLight.color;
    for(int i = 0; i < lights.pointLightCount; i++) {
        PointLight light = lights.pointLights[i];

        float shadow = calcShadow(fragPosition, light.shadowMapIndex, TYPE_POINT);

        vec3 lightToPoint = fragPosition - light.viewPosition;
        vec3 lightDir = normalize(lightToPoint);
        float distance = length(lightToPoint);
        float attenuation = light.attenuationConstant*1+light.attenuationLinear*distance+light.attenuationQuadratic*distance*distance;
        vec3 reflectedColor = color * light.color * light.intensity;
        vec3 diffuse = exposure(fragNormal, lightDir) * reflectedColor/attenuation;
        lighting += diffuse * shadow;

        vec3 reflectedRay = normalize(reflect(lightDir, fragNormal));
        vec3 fragToEye = normalize(-fragPosition);

        lighting += computeSpecular(reflectedColor, specularIntensity, fragToEye, reflectedRay) * shadow;
    }
    for(int i = 0; i < lights.directionalLightCount; i++) {
        DirectionalLight light = lights.directionalLights[i];

        float shadow = calcShadow(fragPosition, light.shadowMapIndex, TYPE_DIRECTIONAL);

        vec3 lightDir = normalize(light.viewDirection);
        vec3 reflectedColor = color * light.color * light.intensity;
        vec3 diffuse = exposure(fragNormal, lightDir) * reflectedColor;
        lighting += diffuse * shadow;

        vec3 reflectedRay = normalize(reflect(lightDir, fragNormal));
        vec3 fragToEye = normalize(-fragPosition);

        lighting += computeSpecular(reflectedColor, specularIntensity, fragToEye, reflectedRay) * shadow;
    }

    for(int i = 0; i < lights.spotLightCount; i++) {
        SpotLight light = lights.spotLights[i];

        float shadow = calcShadow(fragPosition, light.shadowMapIndex, TYPE_SPOT);

        vec3 lightToPoint = fragPosition - light.viewPosition;
        vec3 lightDir = normalize(lightToPoint);

        float spotFactor = max(dot(lightDir, normalize(light.viewDirection)), 0.0);
        if(spotFactor < light.coscutoff) {
            spotFactor = 0.0f;
        } else {
            float minDot = light.coscutoff;
            float dotRange = 1.0f-minDot;
            spotFactor = (spotFactor-minDot)/dotRange;
        }

        float distance = length(lightToPoint);
        float attenuation = light.attenuationConstant*1+light.attenuationLinear*distance+light.attenuationQuadratic*distance*distance;
        vec3 reflectedColor = color * light.color * light.intensity;
        vec3 diffuse = exposure(fragNormal, lightDir) * reflectedColor/attenuation * spotFactor;
        lighting += diffuse * shadow;

        vec3 reflectedRay = normalize(reflect(lightDir, fragNormal));
        vec3 fragToEye = normalize(-fragPosition);

        lighting += computeSpecular(reflectedColor, specularIntensity, fragToEye, reflectedRay) * spotFactor * shadow;
    }
    outColor = vec4(lighting, 1.0);
}