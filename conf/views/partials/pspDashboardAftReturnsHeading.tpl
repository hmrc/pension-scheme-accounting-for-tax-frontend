{% macro render(return) %}
    <h3 class="card-sub-heading bold">
        {% if return.size == 1 %}
            <span class="font-xsmall">
                {{ messages("pspDashboardAftReturnsPartial.span.single", return.startDate, return.endDate) }}
            </span>
            {{ messages("pspDashboardAftReturnsPartial.h3.single") }}
        {% else %}
            <span class="font-xsmall">{{ messages("pspDashboardAftReturnsPartial.span.multiple") }}</span>
            {{ messages("pspDashboardAftReturnsPartial.h3.multiple", return.size) }}
        {% endif %}
    </h3>
{% endmacro %}