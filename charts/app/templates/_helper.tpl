{{/* File contains helper functions which are injected into our templates
See https://pkg.go.dev/text/template for more information on the template functions */}}

{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "labs-helmchart-base.name" -}}
{{- default .Chart.Name .Values.metadata.name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "labs-helmchart-base.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{/*
define metadata labels we will be using in many places.
*/}}
{{- define "labs-helmchart-base.labels" -}}
app.kubernetes.io/name: {{ include "labs-helmchart-base.name" . }}
app.kubernetes.io/instance: {{ include "labs-helmchart-base.buildInstanceName" . }}
helm.sh/chart: {{ include "labs-helmchart-base.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}
{{/*
define required metadata labels that are required in most places.
*/}}
{{- define "labs-helmchart-base.workload-labels" -}}
costcenter: "1120000174"
apmid: "APM0027883"
assignmentgroup: "EIT-ECOMM-JAVIS"
regulatory: "none"
{{- end }}

{{/*
define selectors to tell what pods the deployment will apply to.
*/}}
{{- define "labs-helmchart-base.selector-labels" -}}
app.kubernetes.io/name: {{ include "labs-helmchart-base.name" . }}
app.kubernetes.io/instance: {{ include "labs-helmchart-base.buildInstanceName" . }}
{{- end }}

{{/*
Create the instance name  [package]-[pd|npd]-[name]-[type]
*/}}
{{- define "labs-helmchart-base.buildInstanceName" -}}
{{- $envSuffix := ternary "pd" "npd" (eq .Values.environment "prd") -}}
{{- printf "%s-%s-%s-%s" .Values.metadata.namePrefix $envSuffix .Values.metadata.name .Values.environment | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create the metadata artifact name  [package]-[pd|npd]-[name]-[type]
*/}}
{{- define "labs-helmchart-base.buildName" -}}
{{- $ctx := index . 0 -}}
{{- $artifactType := index . 1 -}}
{{- $envSuffix := ternary "pd" "npd" (eq $ctx.Values.environment "prd") -}}
{{- printf "%s-%s-%s-%s-%s" $ctx.Values.metadata.namePrefix $envSuffix $ctx.Values.metadata.name $ctx.Values.environment $artifactType | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Build the instace name with environment
*/}}
{{- define "labs-helmchart-base.instanceWithEnvironment" -}}
{{- printf "searchindexer-%s" .Values.environment | trunc 63 | trimSuffix "-" -}}
{{- end -}}
