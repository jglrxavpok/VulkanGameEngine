#version 450
#extension GL_ARB_separate_shader_objects : enable
#include "lighting.glsl.h"
layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput gColor;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput gPos;
layout(input_attachment_index = 2, binding = 2) uniform subpassInput gNormal;

layout(location = 0) out vec4 outColor;

float ambientLighting = 0.1f; // TODO: don't hardcode

float exposure(vec3 fragNormal, vec3 lightDirection) {
    return max(dot(-fragNormal, lightDirection), 0.0);
}

void main() {
    vec3 fragPosition = subpassLoad(gPos).xyz;
    vec3 fragNormal = subpassLoad(gNormal).xyz;
    vec3 color = subpassLoad(gColor).rgb;

    vec3 lighting = color * ambientLighting;
    for(int i = 0; i < MAX_POINT_LIGHTS; i++) {
        PointLight light = lights.pointLights[i];

        vec3 lightToPoint = fragPosition - light.viewPosition;
        vec3 lightDir = normalize(lightToPoint);
        float distance = length(lightToPoint);
        float attenuation = light.attenuationConstant*1+light.attenuationLinear*distance+light.attenuationQuadratic*distance*distance;
        vec3 diffuse = exposure(fragNormal, lightDir) * color * light.color * light.intensity/attenuation;
        lighting += diffuse;
    }
    outColor = vec4(lighting, 1.0);
}