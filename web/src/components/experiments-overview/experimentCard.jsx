/* eslint-disable implicit-arrow-linebreak */
import React, { Component } from 'react';
import ReactDOM from 'react-dom';
import './experimentsOverview.css';
import $ from 'jquery';
import uuidv1 from 'uuid/v1';
import { toastr } from 'react-redux-toastr';
import { Line } from 'react-chartjs-2';
import traiangle01 from '../../images/triangle-01.png';
import ArrowButton from '../arrow-button/arrowButton';
import snippetApi from '../../apis/SnippetApi';
import {
  getTimeCreatedAgo,
  generateSummarizedInfo,
} from '../../functions/dataParserHelpers';
import {
  colorsForCharts,
  SKIPPED,
  RUNNING,
  SUCCESS,
  CANCELED,
  FAILED,
  PENDING,
} from '../../dataTypes';

const DataCard = ({ title, linesOfContent }) => (
  <div className="data-card">
    <div className="title">
      <p><b>{title}</b></p>
    </div>
    <div>
      {linesOfContent.map((line, index) => {
        const lineContent = line.startsWith('*')
          ? <b>{line.replace('*', '')}</b>
          : line;
        return <p key={`data-card-${title}-line-cont-${index}`} className="line">{lineContent}</p>;
      })}
    </div>
  </div>
);

class ExperimentCard extends Component {
  constructor(props) {
    super(props);

    this.state = {
      showChart: false,
      chartDivId: new Date().getTime(),
      experiments: this.props.params.experiments,
    };

    this.handleArrowDownButtonClick = this.handleArrowDownButtonClick.bind(this);
  }

  getButtonsDiv(experimentState, index) {
    let buttons;
    const uniqueCode = uuidv1();
    const arrowBtn = (
      <ArrowButton
        imgPlaceHolder={traiangle01}
        callback={this.handleArrowDownButtonClick}
        params={{ ind: index }}
        id={`ArrowButton-${index}`}
        key={`ArrowButton-${uniqueCode}-${index}`}
      />
    );
    if (experimentState === RUNNING || experimentState === PENDING) {
      buttons = [
        arrowBtn,
        <button key={`dangerous-red-${uniqueCode}`} className="dangerous-red" style={{ width: 'max-content' }}><b> Abort </b></button>,
      ];
    } else if (experimentState === SKIPPED) {
      buttons = [
        arrowBtn,
        <button key={`dangerous-red-${uniqueCode}`} className="dangerous-red"><b>X</b></button>,
        <button
          key={`deploy-${uniqueCode}`}
          className="light-green-button experiment-button non-active-black-border"
          style={{ width: '100px' }}
        >
          <b>Resume</b>
        </button>,
      ];
    } else if (experimentState === SUCCESS || experimentState === FAILED) {
      buttons = [
        arrowBtn,
        <button key={`dangerous-red-${uniqueCode}`} className="dangerous-red"><b>X</b></button>,
        <button
          key={`deploy-${uniqueCode}`}
          className="light-green-button experiment-button non-active-black-border"
          style={{ width: '100px' }}
        >
          <b>Deploy</b>
        </button>,
      ];
    } else if (experimentState === CANCELED) {
      buttons = [
        arrowBtn,
        <button key={`dangerous-red-${uniqueCode}`} className="dangerous-red"><b>X</b></button>,
      ];
    }
    return (<div className="buttons-div">{buttons}</div>);
  }

  mapSummarizedInfoToDatasets(summarizedInfo) {
    return summarizedInfo.map(
      (epochObjectVal, index) => {
        const currentValueName = Object.keys(epochObjectVal)[0];
        const dataSet = {};

        dataSet.label = currentValueName;
        dataSet.fill = false;
        dataSet.backgroundColor = colorsForCharts[index];
        dataSet.borderColor = colorsForCharts[index];
        dataSet.lineTension = 0;
        dataSet.data = epochObjectVal[currentValueName];

        return dataSet;
      },
    );
  }

  parseDataAndRefreshChart(jsonExperimentFileParsed, index) {
    const chartDiv = document.getElementById(this.state.chartDivId);
    const cardResults = `${this.state.chartDivId}-Idcard-results-${index}`;
    const exp = this.state.experiments[index];
    const summarizedInfo = generateSummarizedInfo(jsonExperimentFileParsed);
    const dataSets = this.mapSummarizedInfoToDatasets(summarizedInfo);
    const labels = Object.keys(dataSets[0].data);
    const avgValues = Object.keys(summarizedInfo)
      .filter((sInfoItem) => sInfoItem.startsWith('avg_'))
      .map((sInfoItem) => ({ name: sInfoItem.substring(4, sInfoItem.length), value: summarizedInfo[sInfoItem] }));
    exp.data = { labels, datasets: dataSets };
    exp.averageParams = avgValues;

    const newExperimentsArr = this.state.experiments;
    newExperimentsArr[index] = exp;
    this.setState({ experiments: newExperimentsArr });
    if (exp.data) {
      chartDiv.parentNode.childNodes[1].style.display = 'unset';
      $(`#${cardResults}`).css('display', 'flex');
      ReactDOM.render(
        <div>
          <Line data={exp.data} height={50} />
        </div>,
        chartDiv,
      );
    }
  }

  retrieveStatisticsFromApi(index) {
    return snippetApi.getSnippetFile(
      this.props.params.project.id,
      this.state.experiments[index].descTitle.replace('/', '-'),
      'experiment.json',
      'gitlab.com',
    ).then((res) => {
      this.parseDataAndRefreshChart(res, index);
      if (this.state.experiments[index].status === RUNNING
                  || this.state.experiments[index].status === PENDING
      ) {
        setTimeout(() => {
          this.retrieveStatisticsFromApi(index);
        }, 30000);
      }
    }).catch(
      (err) => {
        toastr.warning('Wait', 'No data has been generated for this experiment yet');
      },
    );
  }

  handleArrowDownButtonClick(e, params) {
    const index = params.ind;
    const newState = this.state;
    const chartDiv = document.getElementById(this.state.chartDivId);
    const cardResults = `${this.state.chartDivId}-Idcard-results-${index}`;
    newState.showChart = !this.state.showChart;
    this.setState(
      newState,
    );
    if (newState.showChart) {
      this.retrieveStatisticsFromApi(index);
    } else {
      $(`#${cardResults}`).css('display', 'none');
      chartDiv.parentNode.childNodes[1].style.display = 'none';
      ReactDOM.unmountComponentAtNode(chartDiv);
    }
  }

  render() {
    const { params } = this.props;
    const { chartDivId } = this.state;

    return (
      <div className="experiment-card">
        <div className="header">
          <div className="title-div">
            <p><b>{params.currentState}</b></p>
          </div>
          <div className="select-div">
            <select>
              <option value="">Sort by</option>
            </select>
          </div>
        </div>

        {this.state.experiments.map((experiment, index) => {
          let modelDiv = 'inherit';
          let progressVisibility = 'inherit';
          if (!experiment.percentProgress) {
            modelDiv = 'hidden';
          }
          if (!experiment.modelTitle) {
            progressVisibility = 'hidden';
          }
          return (
            <div
              key={`${experiment.timeCreatedAgo}-${experiment.descTitle}-${index}`}
              className="card-content"
            >
              <div className="summary-data">
                <div className="project-desc-experiment">
                  <button
                    onClick={() => {
                      this.props.setSelectedExperiment(experiment);
                    }}
                    style={{
                      border: 'none',
                      backgroundColor: 'transparent',
                      marginTop: 7,
                      padding: 0,
                    }}
                  >
                    <b>{experiment.descTitle}</b>
                  </button>
                  <p>
  Created by
                    {' '}
                    <b>{experiment.userName}</b>
                    <br />
                    {getTimeCreatedAgo(experiment.timeCreatedAgo)}
                    {' '}
  ago
                  </p>
                </div>
                <div className="project-desc-experiment" style={{ visibility: progressVisibility }}>
                  <p>
                    <b>
                      {experiment.percentProgress}
  % completed
                    </b>

                  </p>
                  <p>
  ETA:
                    {' '}
                    {experiment.eta}
                    {' '}
  hours
                  </p>
                </div>
                <div className="project-desc-experiment" style={{ visibility: modelDiv }}>
                  <p>
  Model:
                    {' '}
                    <b>{experiment.modelTitle}</b>
                  </p>
                  <p>
                    {experiment.averageParams
                      .filter((avgParam) => avgParam.showBellowModel)
                      .map((avgParam) => `${avgParam.name}: ${avgParam.value}`)}
                  </p>
                </div>
                {this.getButtonsDiv(experiment.currentState, index)}
              </div>
              <div className="data-summary">
                <div className="chart-container" id={chartDivId} />
                <div className="content">
                  <p><b>Performace achieved from last epoch:</b></p>
                  {
                        experiment.averageParams.map((opt, index) => (
                          <p key={`${opt.name}-${index}`}>
                            {' '}
                            {`${opt.name}: ${opt.value}`}
                            {' '}
                          </p>
                        ))
                    }
                </div>
              </div>
              <div className="card-results" id={`${chartDivId}-Idcard-results-${index}`}>
                <DataCard
                  title="Data"
                  linesOfContent={[
                    '*3.245 files selected',
                    '  from',
                    '*data instance: DL_pipeline_1',
                    'resulting from a data pipeline with',
                    '*op1: Augment',
                    '*op2: Random Crop',
                    '*op3: Rotate',
                    'sourced from',
                    '*data branch: Master',
                  ]}
                />
                <DataCard
                  title="Algorithm"
                  linesOfContent={[
                    '*Resnet 50',
                    'from',
                    '*branch: feature/3-layers',
                    'authored by',
                    '*Camillo 8 hours ago',
                    'being',
                    '*2 commits and 1 commit behind',
                    'of its master branch',
                  ]}
                />
                <DataCard
                  title="Training"
                  linesOfContent={[
                    '*10 epochs trained',
                    'with',
                    '*P: learning_r = 0.002',
                    '*P: optimizer = adam',
                    '*P: lr_decay = 0.0005',
                    '*P: layers = 3',
                    '*P: dropout = 0.5',
                    '*P: alpha = 1',
                  ]}
                />
              </div>
            </div>
          );
        })}
      </div>
    );
  }
}

export default ExperimentCard;
