// initialise GovUK lib
GOVUKFrontend.initAll();
if (document.querySelector('#country-select') != null) {
    openregisterLocationPicker({
        selectElement: document.getElementById('country-select'),
        url: '/manage-pension-scheme-accounting-for-tax/assets/javascripts/autocomplete/location-autocomplete-graph.json'
    })
}

var backLink = document.querySelector('.govuk-back-link');
if(backLink){
    backLink.addEventListener('click', function(e){
        e.preventDefault();
        if (window.history && window.history.back && typeof window.history.back === 'function'){
            window.history.back();
        }
    });
}