#version 450
#extension GL_ARB_separate_shader_objects : enable
layout (constant_id = 0) const int MAX_LIGHTS = 32;

layout(location = 0) in vec2 fragPos;
layout(location = 1) in vec2 fragCoords;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput gColor;
layout(input_attachment_index = 1, binding = 1) uniform subpassInput gPos;
layout(input_attachment_index = 2, binding = 2) uniform subpassInput gNormal;
layout(binding = 3) uniform LightBuffer {
    int type[MAX_LIGHTS];
    vec3 viewPosition[MAX_LIGHTS];
    vec3 direction[MAX_LIGHTS];
    vec3 color[MAX_LIGHTS];
    float intensity[MAX_LIGHTS];
} ubo;
layout(location = 0) out vec4 outColor;

float ambientLighting = 0.1f; // TODO: don't hardcode

int NoneLightType = 0;
int DirectionalLightType = 1;
int PointLightType = 2;

void main() {
    vec3 fragPosition = subpassLoad(gPos).xyz;
    vec3 fragNormal = subpassLoad(gNormal).xyz;
    vec3 color = subpassLoad(gColor).rgb;

    vec3 lighting = color * ambientLighting;
    for(int i = 0; i < MAX_LIGHTS; i++) {
        if(ubo.type[i] == PointLightType) {
            vec3 lightToPoint = fragPosition - ubo.viewPosition[i];
            vec3 lightDir = normalize(lightToPoint);
            float distance = length(lightToPoint);
            float attenuation = 0.5*1+0.5*distance+0.5*distance*distance; // TODO: custom parameters
            float exposure = max(dot(-fragNormal, lightDir), 0.0); // TODO: fix normals
            vec3 diffuse = exposure * color * ubo.color[i].rgb * ubo.intensity[i]/attenuation;
            lighting += diffuse;
        }
    }
    outColor = vec4(lighting, 1.0);
}