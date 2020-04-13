#version 450
#extension GL_ARB_separate_shader_objects : enable

// TODO: move to other file
layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in vec2 inTexCoords;
layout(location = 3) in vec3 inNormal;

layout(location = 0) out vec3 fragColor;
layout(location = 1) out vec2 fragTexCoord;
layout(location = 2) out vec3 fragWorldPos;
layout(location = 3) out vec3 fragViewNormal;
layout(location = 4) out vec3 fragViewPos;

void main() {
    mat4 modelview = ubo.view * ubo.model;
    vec4 worldPos = ubo.model * vec4(inPosition, 1.0);
    gl_Position = ubo.proj * ubo.view * worldPos;
    fragColor = inColor;
    fragTexCoord = inTexCoords;
    fragWorldPos = worldPos.xyz;
    fragViewNormal = normalize(mat3(modelview) * inNormal);
    fragViewPos = (ubo.view * worldPos).xyz;
}